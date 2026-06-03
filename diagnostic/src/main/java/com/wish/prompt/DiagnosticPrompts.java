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
            You summarize one completed diagnostic investigation turn for long-term session memory.
            Include: the user's request, stages performed, key findings, final conclusion or blocker.
            Use the same language as the user's request. Be concise; omit raw tool logs.
            """;

    public static final String INCIDENT_REPORT_SUMMARY_SYSTEM = """
            You write a concise incident report for archival storage after a diagnostic investigation.
            Include: user request, problem type, root cause (if known), key evidence by stage, final conclusion, \
            impact, and recommended actions.
            Use the same language as the user's request. Do not invent facts; omit raw tool logs.
            """;

    private DiagnosticPrompts() {}

    public static final String DIAGNOSTIC_SYSTEM = """
            You are a senior incident investigator for data and platform systems.
            You coordinate a multi-stage diagnostic workflow. Each stage runs in an isolated \
            context and contributes structured findings back to the main investigation record.
            Be factual, cite evidence from tools, and avoid speculation without labeling it as a hypothesis.
            Two structured classifications are tracked on state:
            - Problem type (classify_incident, CLASSIFY stage): what is wrong / user-visible symptom.
            - Root cause (classify_root_cause, later stages): why it happened; update when evidence changes.
            """;

    public static final String ROUTER_SYSTEM = """
            You are the stage router for an ongoing incident investigation.
            Your only job is to choose the next workflow stage by calling the `router` tool exactly once.
            Do not answer the user directly and do not call any tool other than `router`.
            Stages:
            - CLASSIFY: assign one **problem type** (enum) from the user report — not root cause; required early if unset.
            - COLLECT: gather logs, metrics, lineage, and configuration evidence.
            - ANALYZE: interpret collected evidence, form hypotheses, and narrow root cause.
            - VALIDATE: confirm or refute hypotheses with targeted checks.
            - TERMINATE: investigation is complete; produce the final incident conclusion.
            - ERROR: unrecoverable blocker; document why the investigation cannot continue.
            Choose TERMINATE when findings are sufficient for a actionable conclusion.
            Choose ERROR only when blocked (missing access, invalid request, repeated failures).
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
        return """
                Choose the next investigation stage by calling `router` with the stage name.

                ### Main session snapshot (read-only)
                %s

                ### Structured progress
                %s

                Last completed stage: %s
                Last stage finding: %s

                Current stage marker (before your choice): %s
                """
                .formatted(
                        snapshot,
                        state.desc(),
                        lastStageLine,
                        lastFindingLine,
                        state.getCurrentStage().getName());
    }

    public static String initialIncidentPrompt(String rawRequest) {
        return """
                ## Incident report (user request)

                %s

                Begin routing to the appropriate first stage (typically CLASSIFY, then COLLECT).
                """
                .formatted(rawRequest == null ? "" : rawRequest.trim());
    }

    public static String stageUserPrompt(Stage stage, String routerThought, String backgroundBlock) {
        String thoughtBlock =
                routerThought == null || routerThought.isBlank()
                        ? "(no routing rationale recorded)"
                        : routerThought.trim();
        return """
                ## Stage task: %s

                ### Why this stage was chosen
                %s

                ### Investigation background (main session snapshot at stage entry — read-only)
                %s

                Execute this stage in your isolated context. Tool traces here stay in this stage only.
                %s
                Deliver the stage result only via submit_stage_finding when done.
                """
                .formatted(stage.getName(), thoughtBlock, backgroundBlock, rootCauseToolHint(stage));
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
