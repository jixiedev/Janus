package com.wish.models.session;

import com.wish.models.context.ToolCallUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallSessionTest {

    private static final String AGENT_SYSTEM = "You are a helpful assistant.";

    @Test
    void persistentSession_storesRequestAndResultPairPerPrompt() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        ToolCallSession session = new ToolCallSession("demo", chatMemory, "test summary prompt");

        ToolCallUserContext first = session.beginPrompt("first task");
        first.initializePromptPrefix(AGENT_SYSTEM, "first task");
        first.addMemory(new AssistantMessage("done one"));
        session.endPrompt("Step 1: done one");

        assertTrue(first.getAllMessages().isEmpty());

        ToolCallUserContext second = session.beginPrompt("second task");
        assertEquals("demo", second.getSessionId());
        assertNotEquals(first.getConversation(), second.getConversation());
        assertTrue(second.getAllMessages().isEmpty());

        second.initializePromptPrefix(AGENT_SYSTEM, "second task");
        assertEquals(5, second.getAllMessages().size());
        assertEquals(AGENT_SYSTEM, second.getAllMessages().get(0).getText());
        assertEquals("second task", second.getAllMessages().get(1).getText());
        assertInstanceOf(UserMessage.class, second.getAllMessages().get(2));
        assertTrue(second.getAllMessages().get(2).getText().contains("current task"));
        assertEquals("first task", second.getAllMessages().get(3).getText());
        assertEquals("done one", second.getAllMessages().get(4).getText());
        assertEquals(5, second.getPromptRunStartIndex());

        second.addMemory(new AssistantMessage("done two"));
        session.endPrompt("Step 1: done two");

        var sessionMessages = chatMemory.get(session.getSessionMemoryKey());
        assertEquals(4, sessionMessages.size());
        assertInstanceOf(UserMessage.class, sessionMessages.get(0));
        assertInstanceOf(AssistantMessage.class, sessionMessages.get(1));
        assertInstanceOf(UserMessage.class, sessionMessages.get(2));
        assertInstanceOf(AssistantMessage.class, sessionMessages.get(3));
        assertEquals("second task", sessionMessages.get(2).getText());
        assertEquals("done two", sessionMessages.get(3).getText());
    }

    @Test
    void clear_removesSessionAndContextPartitions() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        ToolCallSession session = new ToolCallSession("x", chatMemory, "test summary prompt");

        ToolCallUserContext context = session.beginPrompt("hello");
        String contextKey = context.getConversation();
        context.initializePromptPrefix(AGENT_SYSTEM, "hello");
        context.addMemory(new AssistantMessage("hi"));
        session.endPrompt("Step 1: hi");

        session.clear();

        assertTrue(chatMemory.get(session.getSessionMemoryKey()).isEmpty());
        assertTrue(chatMemory.get(contextKey).isEmpty());
    }

    @Test
    void initializeContext_bindsPriorSessionSummaryPairs() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        chatMemory.add("session:demo", new UserMessage("prior request"));
        chatMemory.add("session:demo", new AssistantMessage("prior result"));

        ToolCallSession session = new ToolCallSession("demo", chatMemory, "test summary prompt");
        ToolCallUserContext context = session.beginPrompt("new");
        assertTrue(context.getAllMessages().isEmpty());
        context.initializePromptPrefix(AGENT_SYSTEM, "new");
        assertEquals(5, context.getAllMessages().size());
        assertEquals("new", context.getAllMessages().get(1).getText());
        assertEquals("prior request", context.getAllMessages().get(3).getText());
        assertEquals(5, context.getPromptRunStartIndex());
        session.endPrompt();
    }

    @Test
    void abortPrompt_doesNotWriteSessionMemory() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        ToolCallSession session = new ToolCallSession("demo", chatMemory, "test summary prompt");

        ToolCallUserContext context = session.beginPrompt("first");
        context.initializePromptPrefix(AGENT_SYSTEM, "first");
        session.abortPrompt();

        assertTrue(chatMemory.get(session.getSessionMemoryKey()).isEmpty());

        ToolCallUserContext second = session.beginPrompt("second");
        assertTrue(second.getAllMessages().isEmpty());
        session.abortPrompt();
    }

    @Test
    void endPrompt_passesRunResultToSummary() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        ToolCallSession session = new ToolCallSession("demo", chatMemory, "test summary prompt");

        ToolCallUserContext context = session.beginPrompt("analyze data");
        session.endPrompt("Step 1: wrote report\nStep 2: terminate");

        Message last = chatMemory.get(session.getSessionMemoryKey()).get(1);
        assertInstanceOf(AssistantMessage.class, last);
        assertTrue(last.getText().contains("wrote report"));
    }
}
