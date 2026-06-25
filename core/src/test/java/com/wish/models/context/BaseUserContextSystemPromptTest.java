package com.wish.models.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BaseUserContextSystemPromptTest {

    private static final String AGENT_SYSTEM = "You are a helpful assistant.";

    @Test
    void initializePromptPrefix_putsSystemFirst() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        BaseUserContext context = new BaseUserContext("ctx-1", "sess-1", chatMemory);
        context.setPriorTurnSummaries(java.util.List.of());

        context.initializePromptPrefix(AGENT_SYSTEM, "do work");

        assertEquals(2, context.getAllMessages().size());
        assertInstanceOf(SystemMessage.class, context.getAllMessages().get(0));
        assertEquals(AGENT_SYSTEM, context.getAllMessages().get(0).getText());
        assertEquals("do work", context.getAllMessages().get(1).getText());
    }

    @Test
    void initializePromptPrefix_replacesExistingPrefix() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        BaseUserContext context = new BaseUserContext("ctx-2", chatMemory);
        context.initializePromptPrefix(AGENT_SYSTEM, "first");
        context.initializePromptPrefix(AGENT_SYSTEM, "second");

        assertEquals(2, context.getAllMessages().size());
        assertEquals("second", context.getAllMessages().get(1).getText());
    }
}
