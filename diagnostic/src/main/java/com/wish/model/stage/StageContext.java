package com.wish.model.stage;

import java.util.List;

import com.wish.models.context.BaseUserContext;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.messages.Message;

/*
    contains stage specific context, including
        - messages in this stage, system, assistant, user...
        -
 */
public class StageContext {
    private final Stage stage;

    /*
        llm output when calling stageRouter to choose this stage
     */
    private final String llmThought;
    private final List<Message> backgroundInformation;

    @Getter
    private final BaseUserContext baseUserContext;


    public StageContext(BaseUserContext baseUserContext, Stage stage, String llmThought, List<Message> backgroundInformation) {
        this.baseUserContext = baseUserContext;
        this.stage = stage;
        this.llmThought = llmThought;
        this.backgroundInformation = backgroundInformation;
    }

    public List<Message> stageSummary() {
        return List.of();
    }

    /*
        return <SystemPrompt, UserPrompt>
     */
    public Pair<String, String> prepareStagePrompt() {
        //todo how to assemble stage prompt, including system prompt, background, llm thought for this stage, user instruction for this stage
        return Pair.of("", "");
    }
}
