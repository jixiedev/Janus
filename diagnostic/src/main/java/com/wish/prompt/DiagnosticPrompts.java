package com.wish.prompt;

import com.wish.model.stage.Stage;
import com.wish.model.state.State;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * System and user prompts for the diagnostic investigation loop (router + per-stage agents).
 */
public final class DiagnosticPrompts {

    public static final String SUBMIT_STAGE_FINDING_TOOL = "submit_stage_finding";
    public static final String PERSIST_INCIDENT_REPORT_TOOL = "persist_incident_report";

    /**
     * Required incident-timing rules for agent-authored markdown in {@link #PERSIST_INCIDENT_REPORT_TOOL}.
     * Woven into TERMINATE prompts and the persist hint.
     */
    public static final String INCIDENT_REPORT_TIME_RULES = """
            Incident time (required in every report markdown):
            - **Last occurrence, not first**: **Incident occurred** is when the problem **last** happened — the \
            most recent failure or observation within the user's scope. Never use the earliest historical failure \
            unless the user explicitly asked about first occurrence.
            - **User scope first**: Limit to the time window in the user request (e.g. 最近两天 / last 2 days, \
            resolved against Context — investigation time). Older failures go under **Historical context** only.
            - Prefer the latest evidenced timestamp (most recent failed DAG run, latest log line, latest alert) \
            over classify-time estimates.
            - Record when the problem last happened — NOT when the investigation started.
            - If timing within scope is uncertain, use a date range ending at the latest known point and state \
            uncertainty explicitly.
            - Never omit incident timing. Place under "## Incident timeline" or Metadata as **Incident occurred**.
            """;

    /**
     * {@code markdown} param description for {@link com.wish.tools.IncidentReportTool}.
     * Must be a compile-time constant (no {@code .trim()} etc.) for {@code @ToolParam}.
     */
    public static final String INCIDENT_REPORT_MARKDOWN_PARAM_DESCRIPTION =
            "Full incident report in markdown (summary, impact, recommended actions, incident timeline). "
                    + INCIDENT_REPORT_TIME_RULES
                    + " Leave blank to use a structured draft from collected findings.";

    public static final String CLASSIFY_INCIDENT_TOOL = "classify_incident";
    public static final String RECORD_INCIDENT_LAST_OCCURRED_TOOL = "record_incident_last_occurred";
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

    public static String reportPersistHint(boolean reportExists, String targetPath) {
        if (reportExists) {
            return """
                    Instruction: Incident report persistence is enabled.
                    An incident report already exists at: %s
                    Before submit_stage_finding, call persist_incident_report with action=update and the full revised markdown report.

                    %s
                    """
                    .formatted(targetPath, INCIDENT_REPORT_TIME_RULES.trim());
        }
        return """
                Instruction: Incident report persistence is enabled.
                No incident report file exists yet for this session.
                Before submit_stage_finding, call persist_incident_report with action=create and the full markdown report.
                Target path: %s

                %s
                """
                .formatted(targetPath, INCIDENT_REPORT_TIME_RULES.trim());
    }

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
     * Shared investigation background for {@code routeStage} and {@code handleStage}.
     * Loaded as the primary user block; phase-specific prompts follow in trailing blocks.
     */
    public static String sharedInvestigationBackground(
            State state, String userRequest, String priorSessionBlock) {
        return sharedInvestigationBackground(state, userRequest, priorSessionBlock, Instant.now());
    }

    public static String sharedInvestigationBackground(
            State state, String userRequest, String priorSessionBlock, Instant investigationTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context — investigation time (UTC): ")
                .append(formatInvestigationTime(investigationTime))
                .append("\n\n");
        sb.append("Context — user request:\n");
        sb.append(userRequest == null || userRequest.isBlank() ? "(none)" : userRequest.trim());
        String prior = priorSessionBlock == null ? "" : priorSessionBlock.trim();
        if (!prior.isEmpty()) {
            sb.append("\n\nContext — prior session:\n").append(prior);
        }
        sb.append("\n\nContext — structured progress:\n").append(state.desc());
        return sb.toString();
    }

    static String formatInvestigationTime(Instant investigationTime) {
        Instant instant = investigationTime == null ? Instant.now() : investigationTime;
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    /** Route-phase prompt appended after {@link #sharedInvestigationBackground}. */
    public static String routeTurnPrompt(State state) {
        return """
                Instruction: Choose the next investigation stage by calling `router` with the stage name. \
                Do not answer in plain text.

                Context — last stage outcome: %s
                """
                .formatted(formatLastStageOutcome(state));
    }

    private static String formatLastStageOutcome(State state) {
        if (state.getStageFindings().isEmpty()) {
            return "none (first routing decision)";
        }
        var last = state.getStageFindings().getLast();
        return "after stage " + last.getLeft().getName() + ": " + last.getRight();
    }

    public static String initialIncidentPrompt(String rawRequest) {
        return """
                Instruction: Begin routing to the appropriate first stage (typically CLASSIFY, then COLLECT).

                Input — user request:
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
