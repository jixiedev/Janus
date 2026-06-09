package com.wish.support;

import com.wish.agent.base.BaseAgent;
import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import com.wish.models.context.BaseUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectToolResultResolverTest {

    @Test
    void lookupToolResponse_returnsLastMatchingResponse() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        chatMemory.add(
                "conv",
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "id", BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME, "Final answer")))
                        .build());

        assertEquals(
                "Final answer",
                DirectToolResultResolver.lookupToolResponse(
                        chatMemory.get("conv"), BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME));
    }

    @Test
    void extract_usesRegisteredToolBeforeFallback() {
        var messages = new java.util.ArrayList<Message>();
        messages.add(
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "id", BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME, "from tool")))
                        .build());

        String result =
                new TestResolver(BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME, "fallback").extract(messages);

        assertEquals("from tool", result);
    }

    @Test
    void toolCallAgentRegistersDirectResultToolName() {
        ToolCallAgent agent = new ToolCallAgent(() -> new LLMChatClient(null, List.of()), 3);
        assertEquals(BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME, agent.directSessionResultTool());
    }

    @Test
    void extractFallbackWhenToolMissing() {
        assertEquals("fallback", new TestResolver("missing_tool", "fallback").extract(List.of()));
        assertNull(new TestResolver("missing_tool", null).extract(List.of()));
    }

    private static final class TestResolver extends DirectToolResultResolver {

        private final String fallback;

        TestResolver(String registeredToolName, String fallback) {
            super(registeredToolName);
            this.fallback = fallback;
        }

        @Override
        protected String extractFallback(List<Message> messages) {
            return fallback;
        }
    }
}
