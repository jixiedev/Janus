package com.wish.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Tool for returning the final user-visible answer. */
public class CreateChatCompletionTool {

    @Tool(
            name = "create_chat_completion",
            description = "Deliver the complete user-visible answer. Required for every substantive reply; "
                    + "do not duplicate this content in assistant message text. Output plain text unless user explicitly requests markdown.")
    public String createChatCompletion(
            @ToolParam(
                    description = "Full answer text shown to the user (same language as the user, prefer plain text, avoid markdown formatting).",
                    required = true)
            String response) {
        return response;
    }
}
