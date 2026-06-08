package com.wish.models.session;

import com.wish.models.context.Context;
import com.wish.models.context.PromptPrefixLayout;
import lombok.Getter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-process scope for multiple prompt runs ({@link #beginPrompt} / {@link #endPrompt}).
 * Each prompt uses a dedicated {@link Context} (isolated {@link ChatMemory} partition).
 * When {@link #persistent}, {@link #endPrompt} merges context messages into session memory.
 */
@Getter
public abstract class Session<T extends Context> {

    public static final String SESSION_KEY_PREFIX = "session:";
    public static final String CONTEXT_KEY_PREFIX = "context:";

    /** @see com.wish.models.context.PromptPrefixLayout#SESSION_HISTORY_FRAMING */
    public static final String SESSION_HISTORY_FRAMING_PROMPT = PromptPrefixLayout.SESSION_HISTORY_FRAMING;

    private final String sessionId;
    private final ChatMemory chatMemory;
    private final boolean persistent;

    private String currentContextId;
    private T currentContext;
    private String currentPromptRequest;
    private final Map<String, T> historyContext = new LinkedHashMap<>();

    protected Session(String sessionId, ChatMemory chatMemory, boolean persistent) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        this.sessionId = sessionId.trim();
        this.chatMemory = chatMemory;
        this.persistent = persistent;
    }

    public String getSessionMemoryKey() {
        return SESSION_KEY_PREFIX + sessionId;
    }

    public synchronized T beginPrompt(String request) {
        if (currentContext != null) {
            throw new IllegalStateException("Prompt already in progress for session " + sessionId);
        }
        String contextId = CONTEXT_KEY_PREFIX + sessionId + ":" + UUID.randomUUID();
        currentContextId = contextId;
        String normalizedRequest = request == null ? "" : request.trim();
        T context = createContext(contextId, normalizedRequest);
        currentContext = context;
        currentPromptRequest = normalizedRequest;
        context.setPromptRequest(currentPromptRequest);
        historyContext.put(contextId, context);
        initializeContext(context, request);
        return context;
    }

    public synchronized void endPrompt() {
        endPrompt(null);
    }

    public synchronized void endPrompt(String runResult) {
        if (currentContext == null) {
            return;
        }
        T context = currentContext;
        if (runResult != null) {
            // Optimization: provide step-level run output as a summarization hint,
            // especially when the final answer was not emitted via create_chat_completion.
            context.setRunResult(runResult);
        }
        boolean showedProgress = false;
        boolean extracted = false;
        try {
            if (persistent) {
                showEndPromptProgressStart();
                showedProgress = true;
                extractToSessionMemory(context);
                extracted = true;
            }
        } finally {
            if (showedProgress) {
                showEndPromptProgressEnd(extracted);
            }
            // Optimization: keep only session memory across prompts; context partition is one-run scope.
            clearContextMemory(context);
            currentContext = null;
            currentContextId = null;
            currentPromptRequest = null;
        }
    }

    public synchronized void clear() {
        chatMemory.clear(getSessionMemoryKey());
        for (T context : historyContext.values()) {
            clearContextMemory(context);
        }
        historyContext.clear();
        currentContext = null;
        currentContextId = null;
    }

    protected List<Message> getSessionMessages() {
        List<Message> messages = chatMemory.get(getSessionMemoryKey());
        return messages == null ? List.of() : List.copyOf(messages);
    }

    /** Binds prior session summaries on {@code target}; prefix order is applied in {@link Context#initializePromptPrefix}. */
    protected void bindSessionMessages(Context target) {
        target.bindSessionMessages(getSessionMessages());
    }

    /**
     * Ends the current prompt without merging into session memory. Use when the agent run failed
     * so incomplete traces are not summarized into the next {@code -c} request.
     */
    public synchronized void abortPrompt() {
        if (currentContext == null) {
            return;
        }
        T context = currentContext;
        try {
            // intentionally skip extractToSessionMemory
        } finally {
            clearContextMemory(context);
            currentContext = null;
            currentContextId = null;
            currentPromptRequest = null;
        }
    }

    protected void clearContextMemory(T context) {
        chatMemory.clear(context.getConversation());
    }

    protected void showEndPromptProgressStart() {
        System.out.print("[session] end-prompt summarizing... ");
        System.out.flush();
    }

    protected void showEndPromptProgressEnd(boolean success) {
        System.out.println(success ? "done" : "failed");
        System.out.flush();
    }

    protected abstract T createContext(String contextId, String request);

    protected abstract void initializeContext(T context, String request);

    protected abstract void extractToSessionMemory(T context);
}
