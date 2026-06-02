package com.wish.models.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BaseUserContextSystemPromptTest {

  private static final String AGENT_SYSTEM = "You are a helpful assistant.";

  @Test
  void ensureSystemPrompt_prependsWhenHydratedHistoryExists() {
    var chatMemory = MessageWindowChatMemory.builder().build();
    BaseUserContext context = new BaseUserContext("ctx-1", "sess-1", chatMemory);
    context.addMemory(new UserMessage("prior user"));
    context.addMemory(new AssistantMessage("prior assistant"));
    context.addMemory(new UserMessage("session framing"));

    context.ensureSystemPrompt(AGENT_SYSTEM);

    assertEquals(4, context.getAllMessages().size());
    assertInstanceOf(SystemMessage.class, context.getAllMessages().get(0));
    assertEquals(AGENT_SYSTEM, context.getAllMessages().get(0).getText());
    assertEquals("prior user", context.getAllMessages().get(1).getText());
  }

  @Test
  void ensureSystemPrompt_isIdempotent() {
    var chatMemory = MessageWindowChatMemory.builder().build();
    BaseUserContext context = new BaseUserContext("ctx-2", chatMemory);
    context.ensureSystemPrompt(AGENT_SYSTEM);
    context.ensureSystemPrompt(AGENT_SYSTEM);
    assertEquals(1, context.getAllMessages().size());
  }
}
