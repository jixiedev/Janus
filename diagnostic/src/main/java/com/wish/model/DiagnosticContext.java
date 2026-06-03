package com.wish.model;

import com.wish.models.context.BaseUserContext;
import com.wish.model.stage.StageContext;
import com.wish.model.state.State;
import lombok.Getter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class DiagnosticContext {
    private final String contextId;
    private final String rawRequest;

    private final ChatMemory chatMemory;
    public final State currentState;

    /*
        contains
            - initial system prompt
            - initial user request(user message)
            - stage findings(user message)
     */
    private final List<Message> globalInfo = new ArrayList<>();

    /*
        for backup,
        we only need stage summary as user hint to the next steps
     */
    private final LinkedList<List<Message>> historyStageMessages = new LinkedList<>();
    @Getter
    private StageContext currentStageContext;

    public DiagnosticContext(String contextId, String rawRequest, ChatMemory chatMemory, State currentState) {
        this.contextId = contextId;
        this.rawRequest = rawRequest;
        this.chatMemory = chatMemory;
        this.currentState = currentState;
    }

    public void beginStage() {
        //todo id
        BaseUserContext baseUserContext = new BaseUserContext("" + "{stage}" + UUID.randomUUID(), chatMemory);
        this.currentStageContext = new StageContext(
                baseUserContext,
                currentState.currentStage,
                currentState.currentStageThought,
                globalInfo
        );
    }

    public void endStage() {
        List<Message> stageSummary = this.currentStageContext.stageSummary();
        historyStageMessages.add(stageSummary);
    }

    public boolean shouldContinue() {
        return currentState.currentStage.shouldContinue();
    }

    public BaseUserContext getRouteStageContext() {
        //todo
        return null;
    }
}
