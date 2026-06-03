package com.wish.loop;

import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;

import java.util.List;
import java.util.function.Supplier;

/*
inherit from ToolCallAgent, should override
 - system prompt
 - next step prompt
 - other prompt if necessary
 */
public class StageToolCallAgent extends ToolCallAgent {
    public StageToolCallAgent(Supplier<LLMChatClient> chatClientSupplier, int maxSteps) {
        super(chatClientSupplier, maxSteps);
    }

    public StageToolCallAgent(Supplier<LLMChatClient> chatClientSupplier, int maxSteps, List<Object> mcpTools) {
        super(chatClientSupplier, maxSteps, mcpTools);
    }

    public StageToolCallAgent(Supplier<LLMChatClient> chatClientSupplier, int maxSteps, List<Object> mcpTools, List<Object> skillsTools) {
        super(chatClientSupplier, maxSteps, mcpTools, skillsTools);
    }

    protected StageToolCallAgent(String name, String description, String systemPrompt, String nextStepPrompt, String sessionSummarySystemPrompt, Supplier<LLMChatClient> chatClientSupplier, int maxSteps, List<Object> mcpTools, List<Object> builtinTools) {
        super(name, description, systemPrompt, nextStepPrompt, sessionSummarySystemPrompt, chatClientSupplier, maxSteps, mcpTools, builtinTools);
    }

    protected StageToolCallAgent(String name, String description, String systemPrompt, String nextStepPrompt, String sessionSummarySystemPrompt, Supplier<LLMChatClient> chatClientSupplier, int maxSteps, List<Object> mcpTools, List<Object> skillsTools, List<Object> builtinTools) {
        super(name, description, systemPrompt, nextStepPrompt, sessionSummarySystemPrompt, chatClientSupplier, maxSteps, mcpTools, skillsTools, builtinTools);
    }
}
