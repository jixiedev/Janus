package com.wish.models.context;

import com.wish.support.ConversationLlmUsage;
import com.wish.support.promptcache.PromptCacheSupport;
import lombok.Getter;
import org.springframework.ai.chat.memory.ChatMemory;

@Getter
public class PlanningFlowUserContext extends BaseFlowUserContext {

    private final String planId;
    private boolean planInitialized;
    private boolean planFinalized;
    private Context initializeContext;
    private Context finalizeContext;

    public PlanningFlowUserContext(String conversation, ChatMemory chatMemory, String promptRequest) {
        super(conversation, chatMemory);
        this.planId = PromptCacheSupport.planIdFromRequest(null, promptRequest);
        this.initializeContext = new Context(planningMemoryKey("create"), chatMemory);
        this.finalizeContext = new Context(planningMemoryKey("finalize"), chatMemory);
    }

    public static PlanningFlowUserContext forSession(
            String conversation, String sessionId, ChatMemory chatMemory, String promptRequest) {
        PlanningFlowUserContext context =
                new PlanningFlowUserContext(conversation, sessionId, chatMemory, promptRequest);
        context.initializeContext =
                new Context(context.planningMemoryKey("create"), sessionId, chatMemory);
        context.finalizeContext =
                new Context(context.planningMemoryKey("finalize"), sessionId, chatMemory);
        return context;
    }

    private PlanningFlowUserContext(
            String conversation, String sessionId, ChatMemory chatMemory, String promptRequest) {
        super(conversation, sessionId, chatMemory);
        // Optimization: plan id from request fingerprint (not context UUID) for stable planning prefixes.
        this.planId = PromptCacheSupport.planIdFromRequest(sessionId, promptRequest);
    }

    public void clearAllMemoryPartitions(ChatMemory memory) {
        memory.clear(conversation);
        memory.clear(planId);
        if (initializeContext != null) {
            memory.clear(initializeContext.getConversation());
        }
        if (finalizeContext != null) {
            memory.clear(finalizeContext.getConversation());
        }
        for (BaseUserContext executorContext : getFlowContext().values()) {
            memory.clear(executorContext.getConversation());
        }
    }

    public void initializePlan() {
        this.planInitialized = true;
    }

    public void finalizePlan() {
        this.planFinalized = true;
    }

    private String planningMemoryKey(String phase) {
        return conversation + "_planning_" + phase;
    }

    @Override
    public ConversationLlmUsage snapshotLlmUsage() {
        return ConversationLlmUsage.merge(
                super.snapshotLlmUsage(),
                initializeContext.snapshotLlmUsage(),
                finalizeContext.snapshotLlmUsage());
    }
}
