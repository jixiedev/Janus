package com.wish.support;

import com.wish.models.context.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/** Wraps agent runs with conversation LLM usage snapshots and end-of-run logging. */
@Slf4j
public final class AgentRunSupport {

    private AgentRunSupport() {}
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = new ThreadLocal<>();

    public static String currentConversationId() {
        return CURRENT_CONVERSATION_ID.get();
    }

    /** Session id when in a persistent session; otherwise the context partition id. */
    static String resolveToolScopeId(Context context) {
        String sessionId = context.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return context.getConversation();
    }

    public static String runWithTokenLogging(Context context, Supplier<String> action) {
        String previousConversationId = CURRENT_CONVERSATION_ID.get();
        CURRENT_CONVERSATION_ID.set(resolveToolScopeId(context));
        ConversationLlmUsage usageBefore = context.snapshotLlmUsage();
        try {
            return action.get();
        } finally {
            ConversationLlmUsage.logRunSummary(usageBefore, context.snapshotLlmUsage());
            if (previousConversationId == null) {
                CURRENT_CONVERSATION_ID.remove();
            } else {
                CURRENT_CONVERSATION_ID.set(previousConversationId);
            }
        }
    }
}
