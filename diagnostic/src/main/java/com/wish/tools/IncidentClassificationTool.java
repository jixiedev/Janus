package com.wish.tools;

import com.wish.model.incident.IncidentCategory;
import com.wish.model.state.State;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.wish.prompt.DiagnosticPrompts.CLASSIFY_INCIDENT_TOOL;

/**
 * Records problem-type classification on {@link State}. Used only in the CLASSIFY stage.
 * Classifies what is wrong, not why (root cause).
 */
public class IncidentClassificationTool {

    private final State state;

    public IncidentClassificationTool(State state) {
        this.state = state;
    }

    @Tool(
            name = CLASSIFY_INCIDENT_TOOL,
            description =
                    "Set the problem type for this investigation (what is wrong, not root cause). "
                            + "Call once with exactly one enum value, then submit_stage_finding.")
    public String classifyIncident(
            @ToolParam(
                            description =
                                    "Problem type (enum): DATA_ISSUE, JOB_EXECUTION, LATENESS_OR_SLA, PERFORMANCE, "
                                            + "UNAVAILABLE, ACCESS_OR_PERMISSION, REPORTING_OR_UI, ORCHESTRATION, "
                                            + "DATA_FEED_OR_INTEGRATION, USAGE_OR_QUESTION, UNKNOWN",
                            required = true)
                    String category,
            @ToolParam(
                            description =
                                    "Brief rationale describing the reported problem/symptoms only — not root cause.",
                            required = true)
                    String rationale) {
        IncidentCategory resolved = IncidentCategory.require(category);
        String trimmedRationale = rationale == null ? "" : rationale.trim();
        state.setIncidentClassification(resolved, trimmedRationale);
        return "{category=%s, label=%s, rationale=%s}"
                .formatted(resolved.name(), resolved.getLabel(), trimmedRationale);
    }
}
