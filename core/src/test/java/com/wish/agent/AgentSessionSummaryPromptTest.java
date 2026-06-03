package com.wish.agent;

import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AgentSessionSummaryPromptTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void sweAgentSummaryPromptIncludesStructuredSections() {
        Supplier<LLMChatClient> clientSupplier = () -> new LLMChatClient(chatModel, java.util.List.of());
        SWEAgent agent = new SWEAgent(clientSupplier, 10, Path.of("workspace"));

        String prompt = agent.sessionSummarySystemPrompt();
        assertTrue(prompt.contains("Current task goal"));
        assertTrue(prompt.contains("Steps completed"));
        assertTrue(prompt.contains("Key findings"));
        assertTrue(prompt.contains("Unresolved issues"));
        assertTrue(prompt.contains("Next-step plan"));
    }

    @Test
    void agentsUseDistinctSummaryPrompts() {
        Supplier<LLMChatClient> clientSupplier = () -> new LLMChatClient(chatModel, java.util.List.of());
        String toolCall = new ToolCallAgent(clientSupplier, 10).sessionSummarySystemPrompt();
        String janus = new JanusAgent(clientSupplier, 10, Path.of("workspace")).sessionSummarySystemPrompt();
        String swe = new SWEAgent(clientSupplier, 10, Path.of("workspace")).sessionSummarySystemPrompt();

        assertNotEquals(toolCall, janus);
        assertNotEquals(janus, swe);
    }
}
