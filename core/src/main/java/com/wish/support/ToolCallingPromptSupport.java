package com.wish.support;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link org.springframework.ai.model.tool.DefaultToolCallingManager} only copies
 * {@code toolContext} from the prompt when the map is non-empty. Community tools such as
 * {@code FileSystemTools.read(..., ToolContext)} fail validation otherwise.
 */
public final class ToolCallingPromptSupport {

    public static final Map<String, Object> DEFAULT_TOOL_CONTEXT = Map.of("janus", "tool-context");

    private ToolCallingPromptSupport() {}

    /**
     * Rebuilds {@code source} for {@link org.springframework.ai.model.tool.ToolCallingManager#executeToolCalls}:
     * non-empty {@code toolContext} (required by {@code DefaultToolCallingManager}) and
     * {@code internalToolExecutionEnabled(false)} so Janus runs tools in {@code act}, not inside {@code call}.
     */
    public static Prompt prepareExecuteToolCallsPrompt(Prompt source) {
        if (source == null) {
            return null;
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        Map<String, Object> context = Map.of();
        if (source.getOptions() instanceof ToolCallingChatOptions options) {
            if (options.getToolCallbacks() != null) {
                callbacks.addAll(options.getToolCallbacks());
            }
            Map<String, Object> existing = options.getToolContext();
            if (existing != null && !existing.isEmpty()) {
                context = existing;
            }
        }
        if (context.isEmpty()) {
            context = DEFAULT_TOOL_CONTEXT;
        }
        ToolCallingChatOptions executionOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .toolContext(context)
                .internalToolExecutionEnabled(false)
                .build();
        return new Prompt(source.getInstructions(), executionOptions);
    }
}
