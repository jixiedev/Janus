package com.wish.models.session;

import com.wish.models.context.PlanningFlowUserContext;
import com.wish.support.promptcache.PromptCacheSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningFlowSessionTest {

    @Test
    void initializeContext_bindsInitializePartitionAtBeginPrompt() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        chatMemory.add("session:demo", new UserMessage("prior request"));
        chatMemory.add("session:demo", new AssistantMessage("prior result"));

        PlanningFlowSession session = new PlanningFlowSession("demo", chatMemory, null, "test summary prompt");
        PlanningFlowUserContext context = session.beginPrompt("new task");

        assertEquals(
                PromptCacheSupport.planIdFromRequest("demo", "new task"),
                context.getPlanId());

        var planningMessages = context.getInitializeContext().getAllMessages();
        assertTrue(planningMessages.isEmpty());

        context.getInitializeContext().initializePromptPrefix("planning system", "new task");
        var loaded = context.getInitializeContext().getAllMessages();
        assertEquals(5, loaded.size());
        assertEquals("planning system", loaded.get(0).getText());
        assertEquals("new task", loaded.get(1).getText());
        assertInstanceOf(UserMessage.class, loaded.get(2));
        assertTrue(loaded.get(2).getText().contains("current task"));
        assertEquals("prior request", loaded.get(3).getText());
        assertEquals(5, context.getInitializeContext().getPromptRunStartIndex());

        session.endPrompt();
    }
}
