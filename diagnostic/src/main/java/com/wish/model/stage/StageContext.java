package com.wish.model.stage;

import com.wish.models.context.ToolCallUserContext;
import com.wish.prompt.DiagnosticPrompts;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Isolated execution scope for one diagnostic stage.
 * {@link #backgroundSnapshot} is a frozen copy of the main session at {@link com.wish.model.DiagnosticContext#beginStage()};
 * stage tool-call history lives only in {@link #baseUserContext} and never writes back except via
 * {@link #stageFinding} at stage end.
 */
public class StageContext {
    private final Stage stage;
    private final String llmThought;
    /** Main-session snapshot at stage entry; not a live link to main ChatMemory. */
    private final String backgroundSnapshot;

    @Getter
    private final ToolCallUserContext baseUserContext;

    @Getter
    @Setter
    private String stageFinding = "";

    public StageContext(
            ToolCallUserContext baseUserContext,
            Stage stage,
            String llmThought,
            String backgroundSnapshot) {
        this.baseUserContext = baseUserContext;
        this.stage = stage;
        this.llmThought = llmThought;
        this.backgroundSnapshot =
                backgroundSnapshot == null || backgroundSnapshot.isBlank()
                        ? "(no background yet)"
                        : backgroundSnapshot;
    }

    /** @return pair of (systemPrompt, userPrompt) for {@link com.wish.agent.base.BaseAgent#run} */
    public Pair<String, String> prepareStagePrompt() {
        String system = stage.getSystemPrompt();
        String user = DiagnosticPrompts.stageUserPrompt(stage, llmThought, backgroundSnapshot);
        return Pair.of(system, user);
    }
}
