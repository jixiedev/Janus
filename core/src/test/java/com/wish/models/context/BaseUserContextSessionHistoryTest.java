package com.wish.models.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseUserContextSessionHistoryTest {

    @Test
    void initializePromptPrefix_loadsCanonicalOrderIntoMemory() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        BaseUserContext context = new BaseUserContext("ctx", chatMemory);
        context.bindSessionMessages(
                java.util.List.of(new UserMessage("prior"), new AssistantMessage("answer")));

        context.initializePromptPrefix("system", "current task");

        assertEquals(5, context.getAllMessages().size());
        assertEquals("system", context.getAllMessages().get(0).getText());
        assertEquals("current task", context.getAllMessages().get(1).getText());
        assertTrue(context.getAllMessages().get(2).getText().contains("current task"));
        assertEquals("prior", context.getAllMessages().get(3).getText());
        assertEquals("answer", context.getAllMessages().get(4).getText());
        assertEquals(5, context.getPromptRunStartIndex());
        assertTrue(context.getPromptMessages().isEmpty());
    }
}
