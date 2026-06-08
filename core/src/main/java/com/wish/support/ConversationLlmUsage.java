package com.wish.support;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-conversation LLM usage: token totals, prompt-cache metrics, tagging, snapshots, and run-summary logging.
 * {@link com.wish.models.context.Context} delegates recording to a single instance of this class.
 */
@Slf4j
public final class ConversationLlmUsage {

    public static final String DEFAULT_TAG = "default";

    private static volatile boolean runSummaryLoggingEnabled;

    private final TokenTotals tokens;
    private final CacheTotals promptCache;
    private final Map<String, TokenTotals> tokensByTag;
    private final Map<String, CacheTotals> promptCacheByTag;

    public ConversationLlmUsage() {
        this.tokens = new TokenTotals();
        this.promptCache = new CacheTotals();
        this.tokensByTag = new LinkedHashMap<>();
        this.promptCacheByTag = new LinkedHashMap<>();
    }

    private ConversationLlmUsage(
            TokenTotals tokens,
            CacheTotals promptCache,
            Map<String, TokenTotals> tokensByTag,
            Map<String, CacheTotals> promptCacheByTag) {
        this.tokens = tokens;
        this.promptCache = promptCache;
        this.tokensByTag = tokensByTag;
        this.promptCacheByTag = promptCacheByTag;
    }

    public void record(ChatResponse chatResponse) {
        record(chatResponse, DEFAULT_TAG);
    }

    public void record(ChatResponse chatResponse, String usageTag) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        long prompt = intValue(usage.getPromptTokens());
        long completion = intValue(usage.getCompletionTokens());
        long total = intValue(usage.getTotalTokens());
        tokens.add(prompt, completion, total);

        CacheSnapshot cacheSnapshot = cacheFrom(usage);
        promptCache.add(cacheSnapshot);

        String tag = Utils.trimToDefault(usageTag, DEFAULT_TAG);
        tokensByTag.computeIfAbsent(tag, key -> new TokenTotals()).add(prompt, completion, total);
        promptCacheByTag.computeIfAbsent(tag, key -> new CacheTotals()).add(cacheSnapshot);
    }

    public ConversationLlmUsage snapshot() {
        Map<String, TokenTotals> tagTokens = new LinkedHashMap<>();
        tokensByTag.forEach((tag, totals) -> tagTokens.put(tag, totals.copy()));
        Map<String, CacheTotals> tagCache = new LinkedHashMap<>();
        promptCacheByTag.forEach((tag, totals) -> tagCache.put(tag, totals.copy()));
        return new ConversationLlmUsage(tokens.copy(), promptCache.copy(), tagTokens, tagCache);
    }

    public ConversationLlmUsage subtract(ConversationLlmUsage before) {
        if (before == null) {
            return snapshot();
        }
        Map<String, TokenTotals> deltaTokens = deltaTokensByTag(before.tokensByTag, tokensByTag);
        Map<String, CacheTotals> deltaCache = deltaCacheByTag(before.promptCacheByTag, promptCacheByTag);
        return new ConversationLlmUsage(
                tokens.subtract(before.tokens),
                promptCache.subtract(before.promptCache),
                deltaTokens,
                deltaCache);
    }

    public static ConversationLlmUsage merge(ConversationLlmUsage... parts) {
        ConversationLlmUsage merged = new ConversationLlmUsage();
        if (parts == null) {
            return merged.snapshot();
        }
        for (ConversationLlmUsage part : parts) {
            if (part == null) {
                continue;
            }
            merged.tokens.add(part.tokens);
            merged.promptCache.add(part.promptCache);
            part.tokensByTag.forEach(
                    (tag, totals) -> merged.tokensByTag.computeIfAbsent(tag, key -> new TokenTotals()).add(totals));
            part.promptCacheByTag.forEach((tag, totals) ->
                    merged.promptCacheByTag.computeIfAbsent(tag, key -> new CacheTotals()).add(totals));
        }
        return merged.snapshot();
    }

    public static void setRunSummaryLoggingEnabled(boolean enabled) {
        runSummaryLoggingEnabled = enabled;
    }

    public static boolean isRunSummaryLoggingEnabled() {
        return runSummaryLoggingEnabled;
    }

    /** Logs token and prompt-cache deltas for one conversation run (this call vs cumulative). */
    public static void logRunSummary(ConversationLlmUsage before, ConversationLlmUsage after) {
        if (!runSummaryLoggingEnabled || after == null) {
            return;
        }
        ConversationLlmUsage thisCall = after.subtract(before);
        logTokenSummary("Token this call", thisCall.tokens, thisCall.tokensByTag);
        logTokenSummary("Token cumulative", after.tokens, after.tokensByTag);
        logCacheSummary("Prompt cache this call", thisCall.promptCache, thisCall.promptCacheByTag);
        logCacheSummary("Prompt cache cumulative", after.promptCache, after.promptCacheByTag);
    }

    public TokenTotals getTokens() {
        return tokens.copy();
    }

    public CacheTotals getPromptCache() {
        return promptCache.copy();
    }

    public Map<String, TokenTotals> getTokensByTag() {
        Map<String, TokenTotals> copy = new LinkedHashMap<>();
        tokensByTag.forEach((tag, totals) -> copy.put(tag, totals.copy()));
        return Map.copyOf(copy);
    }

    public Map<String, CacheTotals> getPromptCacheByTag() {
        Map<String, CacheTotals> copy = new LinkedHashMap<>();
        promptCacheByTag.forEach((tag, totals) -> copy.put(tag, totals.copy()));
        return Map.copyOf(copy);
    }

    private static void logTokenSummary(String label, TokenTotals totals, Map<String, TokenTotals> byTag) {
        log.info(
                "{} (prompt/completion/total): {}/{}/{}",
                label,
                totals.getPromptTokens(),
                totals.getCompletionTokens(),
                totals.getTotalTokens());
        if (!byTag.isEmpty()) {
            byTag.forEach((tag, usage) -> log.info(
                    "{} [{}] (prompt/completion/total): {}/{}/{}",
                    label,
                    tag,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens()));
        }
    }

    private static void logCacheSummary(String label, CacheTotals totals, Map<String, CacheTotals> byTag) {
        log.info(
                "{} (read/write/prompt-hit%): {}/{}/{}",
                label,
                totals.getCacheReadTokens(),
                totals.getCacheWriteTokens(),
                totals.formatHitRatePercent());
        if (!byTag.isEmpty()) {
            byTag.forEach((tag, usage) -> log.info(
                    "{} [{}] (read/write/prompt-hit%): {}/{}/{}",
                    label,
                    tag,
                    usage.getCacheReadTokens(),
                    usage.getCacheWriteTokens(),
                    usage.formatHitRatePercent()));
        }
    }

    private static Map<String, TokenTotals> deltaTokensByTag(
            Map<String, TokenTotals> before, Map<String, TokenTotals> after) {
        Set<String> tags = new LinkedHashSet<>();
        tags.addAll(before.keySet());
        tags.addAll(after.keySet());
        Map<String, TokenTotals> delta = new LinkedHashMap<>();
        for (String tag : tags) {
            TokenTotals afterTotals = after.getOrDefault(tag, new TokenTotals());
            TokenTotals beforeTotals = before.getOrDefault(tag, new TokenTotals());
            delta.put(tag, afterTotals.subtract(beforeTotals));
        }
        return delta;
    }

    private static Map<String, CacheTotals> deltaCacheByTag(
            Map<String, CacheTotals> before, Map<String, CacheTotals> after) {
        Set<String> tags = new LinkedHashSet<>();
        tags.addAll(before.keySet());
        tags.addAll(after.keySet());
        Map<String, CacheTotals> delta = new LinkedHashMap<>();
        for (String tag : tags) {
            CacheTotals afterTotals = after.getOrDefault(tag, new CacheTotals());
            CacheTotals beforeTotals = before.getOrDefault(tag, new CacheTotals());
            delta.put(tag, afterTotals.subtract(beforeTotals));
        }
        return delta;
    }

    private static CacheSnapshot cacheFrom(Usage usage) {
        long cacheRead = longValue(usage.getCacheReadInputTokens());
        long cacheWrite = longValue(usage.getCacheWriteInputTokens());
        long prompt = longValue(usage.getPromptTokens());

        if (cacheRead == 0 && cacheWrite == 0) {
            CacheSnapshot fromNative = cacheFromNative(usage.getNativeUsage());
            cacheRead = fromNative.cacheReadTokens();
            cacheWrite = fromNative.cacheWriteTokens();
        }

        return new CacheSnapshot(cacheRead, cacheWrite, prompt);
    }

    @SuppressWarnings("unchecked")
    private static CacheSnapshot cacheFromNative(Object nativeUsage) {
        if (!(nativeUsage instanceof Map<?, ?> map)) {
            return CacheSnapshot.empty();
        }
        long cacheRead = longValue(map.get("cache_read_input_tokens"));
        if (cacheRead == 0) {
            cacheRead = longValue(map.get("cacheReadInputTokens"));
        }
        long cacheWrite = longValue(map.get("cache_creation_input_tokens"));
        if (cacheWrite == 0) {
            cacheWrite = longValue(map.get("cacheWriteInputTokens"));
        }
        if (cacheRead == 0) {
            Object details = map.get("prompt_tokens_details");
            if (details instanceof Map<?, ?> detailsMap) {
                cacheRead = longValue(detailsMap.get("cached_tokens"));
            }
        }
        return new CacheSnapshot(cacheRead, cacheWrite, 0);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static long longValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public record CacheSnapshot(long cacheReadTokens, long cacheWriteTokens, long promptTokens) {

        public static CacheSnapshot empty() {
            return new CacheSnapshot(0, 0, 0);
        }
    }

    @Getter
    public static final class TokenTotals {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;

        public TokenTotals add(long promptTokens, long completionTokens, long totalTokens) {
            this.promptTokens += promptTokens;
            this.completionTokens += completionTokens;
            this.totalTokens += totalTokens;
            return this;
        }

        public TokenTotals add(TokenTotals other) {
            if (other == null) {
                return this;
            }
            return add(other.promptTokens, other.completionTokens, other.totalTokens);
        }

        public TokenTotals subtract(TokenTotals other) {
            if (other == null) {
                return copy();
            }
            return new TokenTotals()
                    .add(
                            promptTokens - other.promptTokens,
                            completionTokens - other.completionTokens,
                            totalTokens - other.totalTokens);
        }

        public TokenTotals copy() {
            return new TokenTotals().add(promptTokens, completionTokens, totalTokens);
        }
    }

    @Getter
    public static final class CacheTotals {
        private long cacheReadTokens;
        private long cacheWriteTokens;
        private long promptTokens;
        private int llmCalls;

        public CacheTotals add(CacheSnapshot snapshot) {
            if (snapshot == null) {
                return this;
            }
            cacheReadTokens += snapshot.cacheReadTokens();
            cacheWriteTokens += snapshot.cacheWriteTokens();
            promptTokens += snapshot.promptTokens();
            llmCalls++;
            return this;
        }

        public CacheTotals add(CacheTotals other) {
            if (other == null) {
                return this;
            }
            cacheReadTokens += other.cacheReadTokens;
            cacheWriteTokens += other.cacheWriteTokens;
            promptTokens += other.promptTokens;
            llmCalls += other.llmCalls;
            return this;
        }

        public CacheTotals subtract(CacheTotals other) {
            if (other == null) {
                return copy();
            }
            CacheTotals delta = new CacheTotals();
            delta.cacheReadTokens = cacheReadTokens - other.cacheReadTokens;
            delta.cacheWriteTokens = cacheWriteTokens - other.cacheWriteTokens;
            delta.promptTokens = promptTokens - other.promptTokens;
            delta.llmCalls = llmCalls - other.llmCalls;
            return delta;
        }

        public String formatHitRatePercent() {
            if (promptTokens <= 0) {
                return "n/a";
            }
            double pct = cacheReadTokens * 100.0 / promptTokens;
            return String.format("%.1f%%", pct);
        }

        public CacheTotals copy() {
            CacheTotals copy = new CacheTotals();
            copy.cacheReadTokens = cacheReadTokens;
            copy.cacheWriteTokens = cacheWriteTokens;
            copy.promptTokens = promptTokens;
            copy.llmCalls = llmCalls;
            return copy;
        }
    }
}
