package com.wish.support;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Locale;

/**
 * Resolves a direct-result tool response first; subclasses in agent and session layers supply fallback extraction.
 * Register the tool name via constructor; call {@link #extract(List)} with prompt messages.
 */
public abstract class DirectToolResultResolver {

    private final String registeredToolName;

    protected DirectToolResultResolver(String registeredToolName) {
        this.registeredToolName =
                registeredToolName == null || registeredToolName.isBlank() ? null : registeredToolName.trim();
    }

    protected final String registeredToolName() {
        return registeredToolName;
    }

    /** Registered tool first; {@link #extractFallback(List)} when no matching tool response exists. */
    public final String extract(List<Message> messages) {
        String fromTool = lookupToolResponse(messages, registeredToolName);
        if (fromTool != null && !fromTool.isBlank()) {
            return fromTool.trim();
        }
        return extractFallback(messages);
    }

    protected abstract String extractFallback(List<Message> messages);

    static String lookupToolResponse(List<Message> messages, String toolName) {
        if (messages == null || messages.isEmpty() || toolName == null || toolName.isBlank()) {
            return null;
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (!(message instanceof ToolResponseMessage toolMessage)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolMessage.getResponses()) {
                if (response.name() != null
                        && normalized.equals(response.name().toLowerCase(Locale.ROOT))) {
                    String text = stringify(response.responseData());
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private static String stringify(Object data) {
        return data == null ? null : data.toString();
    }
}
