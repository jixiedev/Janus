package com.wish.models.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextPromptCacheTest {

    @Test
    void onResponse_delegatesToConversationLlmUsage() {
        BaseUserContext context = new BaseUserContext("conv-cache", MessageWindowChatMemory.builder().build());
        ChatResponse first =
                new ChatResponse(
                        List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("a"))),
                        ChatResponseMetadata.builder()
                                .usage(DefaultUsage.fromJson(100, 5, 105, null, 40L, 0L))
                                .build());
        ChatResponse second =
                new ChatResponse(
                        List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("b"))),
                        ChatResponseMetadata.builder()
                                .usage(DefaultUsage.fromJson(150, 8, 158, null, 90L, 10L))
                                .build());

        context.onResponse(first, "step-1");
        context.onResponse(second, "step-2");

        var cumulative = context.snapshotLlmUsage().getPromptCache();
        assertEquals(130, cumulative.getCacheReadTokens());
        assertEquals(10, cumulative.getCacheWriteTokens());
        assertEquals(250, cumulative.getPromptTokens());
        assertEquals(2, cumulative.getLlmCalls());
        assertEquals("52.0%", cumulative.formatHitRatePercent());
    }
}
