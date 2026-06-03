package com.wish.loop;

import com.wish.llm.LLMChatClient;
import com.wish.model.stage.Stage;
import com.wish.model.stage.StageContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Supplier;

public class StageProcessor {
    private final StageContext stageContext;
    private final Stage stage;

    private final StageToolCallAgent stageToolCallAgent;

    public StageProcessor(StageContext stageContext, Stage stage, Supplier<LLMChatClient> llmChatClientSupplier) {
        this.stageContext = stageContext;
        this.stage = stage;
        this.stageToolCallAgent = new StageToolCallAgent(llmChatClientSupplier, stage.getMaxSteps());
    }

    public void execute() {
        Pair<String, String> stagePrompt = stageContext.prepareStagePrompt();
        this.stageToolCallAgent.run(stageContext.getBaseUserContext(), stagePrompt.getRight());
    }
}
