package com.wish.model.stage;

import com.wish.models.context.ToolCallUserContext;
import com.wish.prompt.DiagnosticPrompts;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Isolated execution scope for one diagnostic stage.
 * {@link #sharedInvestigationBackground} matches {@link com.wish.model.DiagnosticContext#formatSharedInvestigationBackground()}
 * at stage entry; stage tool-call history lives only in {@link #baseUserContext}.
 */
public class StageContext {
    private final Stage stage;
    private final String llmThought;
    private final String sharedInvestigationBackground;

    @Getter
    private final ToolCallUserContext baseUserContext;

    @Getter
    @Setter
    private String stageFinding = "";

    @Getter
    @Setter
    private String reportPersistHint = "";

    public StageContext(
            ToolCallUserContext baseUserContext,
            Stage stage,
            String llmThought,
            String sharedInvestigationBackground) {
        this.baseUserContext = baseUserContext;
        this.stage = stage;
        this.llmThought = llmThought;
        this.sharedInvestigationBackground =
                sharedInvestigationBackground == null || sharedInvestigationBackground.isBlank()
                        ? "Context — structured progress:\n(empty)"
                        : sharedInvestigationBackground.trim();
    }

    public String getSharedInvestigationBackground() {
        return sharedInvestigationBackground;
    }

    /** Stage-phase prompts appended after {@link #getSharedInvestigationBackground()}. */
    public List<String> stageTurnPromptBlocks() {
        List<String> blocks = new ArrayList<>();
        blocks.add(DiagnosticPrompts.stageTaskUserPrompt(stage, llmThought));
        if (reportPersistHint != null && !reportPersistHint.isBlank()) {
            blocks.add(reportPersistHint.trim());
        }
        return List.copyOf(blocks);
    }
}
