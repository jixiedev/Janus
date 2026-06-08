package com.wish.loop;

import com.wish.llm.LLMChatClient;
import com.wish.model.stage.Stage;
import com.wish.model.stage.StageContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.function.Supplier;

public class StageProcessor {
    private final StageContext stageContext;
    private final Stage stage;
    private final StageToolCallAgent stageToolCallAgent;

    public StageProcessor(
            StageContext stageContext,
            Stage stage,
            Supplier<LLMChatClient> llmChatClientSupplier,
            List<Object> extraStageTools) {
        this.stageContext = stageContext;
        this.stage = stage;
        this.stageToolCallAgent =
                StageToolCallAgent.forStage(stage, llmChatClientSupplier, extraStageTools);
    }

    public void execute() {
        Pair<String, String> stagePrompt = stageContext.prepareStagePrompt();
        stageToolCallAgent.setSystemPrompt(stagePrompt.getLeft());
        // Optimization: stage system + task user message precede variable background for prefix cache hits.
        String finding =
                stageToolCallAgent.run(
                        stageContext.getBaseUserContext(),
                        stagePrompt.getRight(),
                        List.of(stageContext.stageBackgroundUserPrompt()));
        stageContext.setStageFinding(finding == null ? "" : finding.trim());
    }
}
