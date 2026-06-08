package com.wish.models.context;

import com.wish.agent.base.BaseAgent;
import com.wish.support.ConversationLlmUsage;
import lombok.Getter;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
public class BaseFlowUserContext extends Context {
    private final Map<BaseAgent, BaseUserContext> flowContext = new HashMap<>();

    public BaseFlowUserContext(String conversation, ChatMemory chatMemory) {
        super(conversation, chatMemory);
    }

    public BaseFlowUserContext(String conversation, String sessionId, ChatMemory chatMemory) {
        super(conversation, sessionId, chatMemory);
    }

    /** One {@link BaseUserContext} per executor agent (separate memory partition per agent). */
    public void setupExecutors(Set<BaseAgent> executors) {
        flowContext.clear();
        int i = 0;
        String sessionId = getSessionId();
        for (BaseAgent agent : executors) {
            String executorConv = conversation + "_executor_" + i;
            i++;
            if (sessionId != null && !sessionId.isBlank()) {
                flowContext.put(agent, agent.createUserContext(executorConv, sessionId, chatMemory));
            } else {
                flowContext.put(agent, agent.createUserContext(executorConv, chatMemory));
            }
        }
    }

    public BaseUserContext getExecutorContext(BaseAgent agent) {
        return flowContext.get(agent);
    }

    public boolean hasExecutorContexts() {
        return !flowContext.isEmpty();
    }

    @Override
    public ConversationLlmUsage snapshotLlmUsage() {
        ConversationLlmUsage[] parts = new ConversationLlmUsage[flowContext.size() + 1];
        parts[0] = super.snapshotLlmUsage();
        int i = 1;
        for (BaseUserContext context : flowContext.values()) {
            parts[i++] = context.snapshotLlmUsage();
        }
        return ConversationLlmUsage.merge(parts);
    }
}
