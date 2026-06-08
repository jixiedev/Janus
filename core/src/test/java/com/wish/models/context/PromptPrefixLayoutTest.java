package com.wish.models.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptPrefixLayoutTest {

    @Test
    void build_ordersSystemTaskThenSessionHistory() {
        List<org.springframework.ai.chat.messages.Message> messages =
                PromptPrefixLayout.build(
                        "You are helpful.",
                        List.of(new UserMessage("prior"), new AssistantMessage("answer")),
                        "current task",
                        List.of("background block"));

        assertEquals(6, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals("You are helpful.", messages.get(0).getText());
        assertEquals("current task", messages.get(1).getText());
        assertEquals("background block", messages.get(2).getText());
        assertTrue(messages.get(3).getText().contains("current task"));
        assertEquals("prior", messages.get(4).getText());
        assertEquals("answer", messages.get(5).getText());
    }
}
