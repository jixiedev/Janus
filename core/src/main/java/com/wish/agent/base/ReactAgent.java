package com.wish.agent.base;

import com.wish.models.context.BaseUserContext;
import com.wish.llm.LLMChatClient;

import java.util.function.Supplier;

public abstract class ReactAgent extends BaseAgent {

    protected ReactAgent(
            String name,
            String description,
            String systemPrompt,
            String nextStepPrompt,
            Supplier<LLMChatClient> chatClientSupplier,
            int maxSteps) {
        super(name, description, systemPrompt, nextStepPrompt, chatClientSupplier, maxSteps);
    }

    @Override
    public String step(BaseUserContext userContext) {
        boolean shouldAct = think(userContext);
        if (!shouldAct) {
            return "Thinking complete - no action needed";
        }
        return act(userContext);
    }

    public abstract boolean think(BaseUserContext userContext);

    public abstract String act(BaseUserContext userContext);
}
