package com.wish.support;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationLlmUsageTest {

    @Test
    void record_readsSpringAiCacheFields() {
        ConversationLlmUsage usage = new ConversationLlmUsage();
        usage.record(
                responseWithUsage(DefaultUsage.fromJson(200, 10, 210, null, 120L, 5L)),
                "step-1");

        ConversationLlmUsage.CacheTotals cache = usage.getPromptCache();
        assertEquals(120, cache.getCacheReadTokens());
        assertEquals(5, cache.getCacheWriteTokens());
        assertEquals("60.0%", cache.formatHitRatePercent());
    }

    @Test
    void record_readsOpenAiNativeCachedTokens() {
        ConversationLlmUsage usage = new ConversationLlmUsage();
        usage.record(
                responseWithUsage(
                        new DefaultUsage(
                                300,
                                20,
                                320,
                                Map.of("prompt_tokens_details", Map.of("cached_tokens", 256)))),
                "step-1");

        assertEquals(256, usage.getPromptCache().getCacheReadTokens());
        assertEquals("85.3%", usage.getPromptCache().formatHitRatePercent());
    }

    @Test
    void subtract_computesRunDelta() {
        ConversationLlmUsage usage = new ConversationLlmUsage();
        usage.record(responseWithUsage(DefaultUsage.fromJson(100, 5, 105, null, 40L, 0L)), "a");
        ConversationLlmUsage before = usage.snapshot();
        usage.record(responseWithUsage(DefaultUsage.fromJson(150, 8, 158, null, 90L, 10L)), "b");
        ConversationLlmUsage delta = usage.snapshot().subtract(before);

        assertEquals(250, usage.getTokens().getPromptTokens());
        assertEquals(90, delta.getPromptCache().getCacheReadTokens());
        assertEquals(150, delta.getPromptCache().getPromptTokens());
    }

    private static org.springframework.ai.chat.model.ChatResponse responseWithUsage(
            org.springframework.ai.chat.metadata.Usage usage) {
        return new org.springframework.ai.chat.model.ChatResponse(
                java.util.List.of(
                        new org.springframework.ai.chat.model.Generation(
                                new org.springframework.ai.chat.messages.AssistantMessage("ok"))),
                org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(usage).build());
    }
}
