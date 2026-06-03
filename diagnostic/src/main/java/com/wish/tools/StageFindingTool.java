package com.wish.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.wish.prompt.DiagnosticPrompts.SUBMIT_STAGE_FINDING_TOOL;

/** Records the structured output of a completed diagnostic stage. */
public class StageFindingTool {

    @Tool(
            name = SUBMIT_STAGE_FINDING_TOOL,
            description =
                    "Submit the complete finding for the current investigation stage. "
                            + "Include evidence, conclusions, and open questions. Required before terminating the stage.")
    public String submitStageFinding(
            @ToolParam(
                            description =
                                    "Structured stage finding (plain text; same language as the incident report).",
                            required = true)
                    String finding) {
        return finding == null ? "" : finding.trim();
    }
}
