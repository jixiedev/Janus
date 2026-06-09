package com.wish.models.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

/**
 * Per-prompt-run state for agent {@code run}. One instance per {@link com.wish.models.session.Session#beginPrompt};
 * shell services cache {@link com.wish.models.session.Session}, not individual contexts.
 */
@Getter
public class BaseUserContext extends Context {

    private int currentStep;

    @Setter
    private String lastThinkResult = "";

    @Setter
    private boolean userOutputEnabled = true;

    public BaseUserContext(String conversationId, ChatMemory chatMemory) {
        this(conversationId, null, chatMemory);
    }

    public BaseUserContext(String conversationId, String sessionId, ChatMemory chatMemory) {
        super(conversationId, sessionId, chatMemory);
        this.currentStep = 0;
    }

    public void beginRun() {
        currentStep = 0;
        lastThinkResult = "";
    }

    public int nextStep() {
        return ++currentStep;
    }

    public boolean isStuck(int duplicateThreshold) {
        List<Message> messages = chatMemory.get(conversation);
        if (messages.size() < 2) {
            return false;
        }

        final String lastAssistantText = getLastAssistantText();
        if (lastAssistantText.isBlank()) {
            return false;
        }

        long duplicate = messages.stream()
                .filter(message -> message.getMessageType() == MessageType.ASSISTANT
                        && lastAssistantText.equals(message.getText()))
                .count();
        return duplicate > duplicateThreshold;
    }

    public boolean isUserOutputEnabled() {
        return userOutputEnabled;
    }
}
