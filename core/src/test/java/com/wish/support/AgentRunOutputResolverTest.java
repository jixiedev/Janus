package com.wish.support;

import com.wish.agent.base.BaseAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunOutputResolverTest {

    @Test
    void findLastToolResponse_returnsMatchingTool() {
        var chatMemory = MessageWindowChatMemory.builder().build();
        chatMemory.add(
                "conv",
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "id",
                                BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME,
                                "## Wish\n\nFounded 2010.")))
                        .build());

        String text = AgentRunOutputResolver.findLastToolResponse(
                chatMemory.get("conv"), BaseAgent.CREATE_CHAT_COMPLETION_TOOL_NAME);

        assertEquals("## Wish\n\nFounded 2010.", text);
    }

    @Test
    void extractUserVisibleFromSteps_skipsJsonSteps() {
        String visible = AgentRunOutputResolver.extractUserVisibleFromSteps(
                null,
                List.of(
                        "Step 1: [{\"text\":\"noise\"}]",
                        "Step 2: ## Wish\n\nFounded 2010."));

        assertEquals("## Wish\n\nFounded 2010.", visible);
    }

    @Test
    void isReadableToolResult_filtersJson() {
        assertFalse(AgentRunOutputResolver.isReadableToolResult("[{\"text\":\"x\"}]"));
        assertTrue(AgentRunOutputResolver.isReadableToolResult("Readable answer with enough text for display."));
    }
}
