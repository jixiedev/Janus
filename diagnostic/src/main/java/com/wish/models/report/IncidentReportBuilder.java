package com.wish.models.report;

import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.stage.Stage;
import com.wish.model.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a concise incident report from structured investigation state (no LLM).
 * Used as a fallback draft when the agent omits markdown in {@code persist_incident_report}.
 */
public final class IncidentReportBuilder {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private IncidentReportBuilder() {}

    public static String build(DiagnosticContext context) {
        if (context == null) {
            return "# Incident report\n\n(empty context)\n";
        }
        State state = context.currentState;
        String request = context.getRawRequest() == null ? "" : context.getRawRequest().trim();
        String conclusion = resolveConclusion(context);

        StringBuilder sb = new StringBuilder();
        sb.append("# Incident report\n\n");
        sb.append("## Metadata\n\n");
        sb.append("- **Updated**: ").append(TIMESTAMP.format(Instant.now())).append('\n');
        if (state.getIncidentOccurredAt() != null) {
            sb.append("- **Incident last occurred (classified)**: ")
                    .append(TIMESTAMP.format(state.getIncidentOccurredAt()))
                    .append('\n');
        }
        if (context.getSessionId() != null && !context.getSessionId().isBlank()) {
            sb.append("- **Session**: ").append(context.getSessionId().trim()).append('\n');
        }
        sb.append("- **Final stage**: ").append(state.getCurrentStage().name()).append("\n\n");

        sb.append("## User request\n\n");
        sb.append(request.isBlank() ? "(empty)\n\n" : request + "\n\n");

        appendClassification(sb, state);
        appendStageFindings(sb, state.getStageFindings());
        sb.append("## Conclusion\n\n");
        sb.append(conclusion.isBlank() ? "(pending)\n" : conclusion + "\n");
        return sb.toString();
    }

    private static void appendClassification(StringBuilder sb, State state) {
        sb.append("## Classification\n\n");
        IncidentCategory problem = state.getIncidentCategory();
        if (problem != null) {
            sb.append("- **Problem**: ")
                    .append(problem.name())
                    .append(" (")
                    .append(problem.getLabel())
                    .append(")\n");
            if (state.getIncidentCategoryRationale() != null
                    && !state.getIncidentCategoryRationale().isBlank()) {
                sb.append("- **Problem summary**: ")
                        .append(state.getIncidentCategoryRationale().trim())
                        .append('\n');
            }
        } else {
            sb.append("- **Problem**: (not assigned)\n");
        }

        IncidentRootCause cause = state.getRootCause();
        if (cause != null) {
            sb.append("- **Root cause**: ")
                    .append(cause.name())
                    .append(" (")
                    .append(cause.getLabel())
                    .append(")\n");
            RootCauseConfidence confidence = state.getRootCauseConfidence();
            if (confidence != null) {
                sb.append("- **Confidence**: ").append(confidence.name()).append('\n');
            }
            if (state.getRootCauseRationale() != null && !state.getRootCauseRationale().isBlank()) {
                sb.append("- **Root cause rationale**: ")
                        .append(state.getRootCauseRationale().trim())
                        .append('\n');
            }
        } else {
            sb.append("- **Root cause**: (not assigned)\n");
        }
        sb.append('\n');
    }

    private static void appendStageFindings(StringBuilder sb, List<Pair<Stage, String>> findings) {
        sb.append("## Stage findings\n\n");
        if (findings == null || findings.isEmpty()) {
            sb.append("(none)\n\n");
            return;
        }
        for (Pair<Stage, String> entry : findings) {
            sb.append("### ").append(entry.getLeft().name()).append("\n\n");
            String finding = entry.getRight() == null ? "" : entry.getRight().trim();
            sb.append(finding.isBlank() ? "(empty)\n\n" : finding + "\n\n");
        }
    }

    private static String resolveConclusion(DiagnosticContext context) {
        if (!context.getStageContributions().isEmpty()) {
            DiagnosticContext.StageContribution last =
                    context.getStageContributions().getLast();
            if (last.finding() != null && !last.finding().isBlank()) {
                return last.finding().trim();
            }
        }
        return context.buildRunResult();
    }
}
