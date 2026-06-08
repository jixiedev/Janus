package com.wish.prompt;

import com.wish.model.stage.Stage;
import com.wish.model.state.State;

/**
 * System and user prompts for the diagnostic investigation loop (router + per-stage agents).
 */
public final class DiagnosticPrompts {

    public static final String SUBMIT_STAGE_FINDING_TOOL = "submit_stage_finding";
    public static final String CLASSIFY_INCIDENT_TOOL = "classify_incident";
    public static final String CLASSIFY_ROOT_CAUSE_TOOL = "classify_root_cause";

    public static final String SESSION_SUMMARY_SYSTEM = """
            Instruction: Summarize one completed diagnostic investigation turn for long-term session memory.

            Input: User request and investigation trace will follow in the user message.

            Output: Write a concise summary covering:
            - The user's request
            - Stages performed
            - Key findings
            - Final conclusion or blocker

            Rules: Use the same language as the user's request. Omit raw tool logs.
            """;

    public static final String INCIDENT_REPORT_SUMMARY_SYSTEM = """
            Instruction: Write a concise incident report for archival storage after a diagnostic investigation.

            Input: Investigation context will follow in the user message.

            Output: Include:
            - User request
            - Problem type
            - Root cause (if known)
            - Key evidence by stage
            - Final conclusion
            - Impact
            - Recommended actions

            Rules: Use the same language as the user's request. Do not invent facts. Omit raw tool logs.
            """;

    private DiagnosticPrompts() {}

    public static final String DIAGNOSTIC_SYSTEM = """
            Role: You are a senior incident investigator for data and platform systems.

            Instruction: Coordinate a multi-stage diagnostic workflow. Each stage runs in an isolated \
            context and contributes structured findings back to the main investigation record.

            Rules:
            - Be factual; cite evidence from tools.
            - Label speculation as hypothesis.
            - Problem type (classify_incident, CLASSIFY stage): what is wrong / user-visible symptom.
            - Root cause (classify_root_cause, later stages): why it happened; update when evidence changes.
            """;

    public static final String ROUTER_SYSTEM = """
            Role: You are the stage router for an ongoing incident investigation.

            Instruction: Choose the next workflow stage by calling the `router` tool exactly once.

            Output: Call `router` with one stage name only. Do not answer the user directly. \
            Do not call any tool other than `router`.

            Stages:
            - CLASSIFY: assign one **problem type** (enum) from the user report — not root cause; required early if unset.
            - COLLECT: gather logs, metrics, lineage, and configuration evidence.
            - ANALYZE: interpret collected evidence, form hypotheses, and narrow root cause.
            - VALIDATE: confirm or refute hypotheses with targeted checks.
            - TERMINATE: investigation is complete; produce the final incident conclusion.
            - ERROR: unrecoverable blocker; document why the investigation cannot continue.

            Rules:
            - Choose TERMINATE when findings are sufficient for an actionable conclusion.
            - Choose ERROR only when blocked (missing access, invalid request, repeated failures).
            """;

    /**
     * Per-turn router user message. {@code mainSessionSnapshot} is a boundary copy from the main session;
     * it must not be persisted as part of stage chat history.
     */
    public static String routeStageUserPrompt(State state, String mainSessionSnapshot) {
        String lastStageLine = "none (this is the first routing decision)";
        String lastFindingLine = "none";
        if (!state.getStageFindings().isEmpty()) {
            var last = state.getStageFindings().getLast();
            lastStageLine = last.getLeft().getName();
            lastFindingLine = last.getRight();
        }
        String snapshot =
                mainSessionSnapshot == null || mainSessionSnapshot.isBlank()
                        ? "(no main session content yet)"
                        : mainSessionSnapshot.trim();
        // Optimization: stable routing instructions first; variable main-session snapshot at the tail.
        return """
                Instruction: Choose the next investigation stage by calling `router` with the stage name. \
                Do not answer in plain text.

                Context — structured progress:
                %s

                Context — last completed stage: %s
                Context — last stage finding: %s
                Context — current stage marker (before your choice): %s

                Input — main session snapshot (read-only):
                %s
                """
                .formatted(
                        state.desc(),
                        lastStageLine,
                        lastFindingLine,
                        state.getCurrentStage().getName(),
                        snapshot);
    }

    public static String initialIncidentPrompt(String rawRequest) {
        return """
                Instruction: Begin routing to the appropriate first stage (typically CLASSIFY, then COLLECT).

                Input — incident report (user request):
                %s
                """
                .formatted(rawRequest == null ? "" : rawRequest.trim());
    }

    /** Stable stage-task header (prompt-cache prefix within a stage's multi-step loop). */
    public static String stageTaskUserPrompt(Stage stage, String routerThought) {
        String thoughtBlock =
                routerThought == null || routerThought.isBlank()
                        ? "(no routing rationale recorded)"
                        : routerThought.trim();
        return """
                Instruction: Execute stage %s in your isolated context. Tool traces here stay in this stage only.

                Context — why this stage was chosen:
                %s

                Output: Deliver the stage result only via submit_stage_finding when done.
                %s
                """
                .formatted(stage.getName(), thoughtBlock, rootCauseToolHint(stage));
    }

    /** Variable investigation background; appended after {@link #stageTaskUserPrompt} for cache-friendly ordering. */
    public static String stageBackgroundUserPrompt(String backgroundBlock) {
        String background =
                backgroundBlock == null || backgroundBlock.isBlank()
                        ? "(no background yet)"
                        : backgroundBlock.trim();
        return """
                Context — investigation background (main session snapshot at stage entry — read-only):
                %s
                """
                .formatted(background);
    }

    public static String stageUserPrompt(Stage stage, String routerThought, String backgroundBlock) {
        return stageTaskUserPrompt(stage, routerThought) + "\n\n" + stageBackgroundUserPrompt(backgroundBlock);
    }

    private static String rootCauseToolHint(Stage stage) {
        if (!stage.allowsRootCauseClassification()) {
            return "";
        }
        return """
                When evidence supports a root-cause hypothesis, call classify_root_cause (may update prior assignments).
                Allowed root causes:
                """
                + com.wish.model.incident.IncidentRootCause.catalogForPrompt()
                + "\n";
    }

    public static String stageFindingMessage(Stage stage, String finding) {
        return "## Finding from stage %s\n\n%s".formatted(stage.getName(), finding == null ? "" : finding.trim());
    }

    public static String backgroundFromMessages(java.util.List<org.springframework.ai.chat.messages.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no background yet)";
        }
        StringBuilder sb = new StringBuilder();
        for (var message : messages) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append(text.trim()).append("\n\n");
        }
        return sb.isEmpty() ? "(no background yet)" : sb.toString().trim();
    }
}
