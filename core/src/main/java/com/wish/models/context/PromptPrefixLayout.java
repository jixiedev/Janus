package com.wish.models.context;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical LLM prompt prefix ordering for prompt-cache reuse.
 * <p>
 * Order: {@code System} → primary user task → optional trailing user blocks → session framing →
 * prior session summaries. Agent step messages ({@code A/T}) append after this prefix.
 */
public final class PromptPrefixLayout {

    /**
     * Inserted after the current user task(s) and before hydrated session summaries.
     * Optimization: the active task stays directly under {@code System} for prefix-cache stability.
     */
    public static final String SESSION_HISTORY_FRAMING = """
            The user message above is the current task.
            The messages below are summarized prior turns in this session (background only).
            """;

    private PromptPrefixLayout() {}

    /**
     * @param systemPrompt agent or phase system text (may be blank)
     * @param sessionMessages summarized prior turns from {@link com.wish.models.session.Session}
     * @param primaryUserRequest current shell/API task
     * @param trailingUserBlocks variable background appended after the primary task, before session history
     */
    public static List<Message> build(
            String systemPrompt,
            List<Message> sessionMessages,
            String primaryUserRequest,
            List<String> trailingUserBlocks) {
        List<Message> messages = new ArrayList<>();
        appendSystem(messages, systemPrompt);
        appendUser(messages, primaryUserRequest);
        appendTrailingUsers(messages, trailingUserBlocks);
        appendSessionHistory(messages, sessionMessages);
        return List.copyOf(messages);
    }

    public static List<Message> build(
            String systemPrompt, List<Message> sessionMessages, String primaryUserRequest) {
        return build(systemPrompt, sessionMessages, primaryUserRequest, List.of());
    }

    private static void appendSystem(List<Message> messages, String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return;
        }
        messages.add(new SystemMessage(systemPrompt.trim()));
    }

    private static void appendUser(List<Message> messages, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        messages.add(new UserMessage(text.trim()));
    }

    private static void appendTrailingUsers(List<Message> messages, List<String> trailingUserBlocks) {
        if (trailingUserBlocks == null || trailingUserBlocks.isEmpty()) {
            return;
        }
        for (String block : trailingUserBlocks) {
            appendUser(messages, block);
        }
    }

    private static void appendSessionHistory(List<Message> messages, List<Message> sessionMessages) {
        if (sessionMessages == null || sessionMessages.isEmpty()) {
            return;
        }
        messages.add(new UserMessage(SESSION_HISTORY_FRAMING));
        messages.addAll(sessionMessages);
    }
}
