package com.wish.tools;

import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.state.State;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.wish.prompt.DiagnosticPrompts.CLASSIFY_ROOT_CAUSE_TOOL;

/**
 * Updates root-cause classification on {@link State} during investigation (COLLECT onward).
 * May be called multiple times; each call replaces the previous assignment when evidence changes.
 */
public class RootCauseClassificationTool {

    private final State state;

    public RootCauseClassificationTool(State state) {
        this.state = state;
    }

    @Tool(
            name = CLASSIFY_ROOT_CAUSE_TOOL,
            description =
                    "Set or update the root-cause category for this investigation based on current evidence. "
                            + "Call when analysis supports a cause; call again if a later stage changes the conclusion. "
                            + "Distinct from classify_incident (problem type only).")
    public String classifyRootCause(
            @ToolParam(
                            description =
                                    "Root cause (enum): CODE_OR_LOGIC, UPSTREAM_DATA, MISCONFIGURATION, "
                                            + "DEPLOYMENT_OR_RELEASE, INFRASTRUCTURE, CAPACITY_OR_RESOURCE, ORCHESTRATION, "
                                            + "PERMISSION_OR_CREDENTIAL, EXTERNAL_VENDOR, OPERATOR_ERROR, NOT_APPLICABLE, UNKNOWN",
                            required = true)
                    String cause,
            @ToolParam(
                            description = "Evidence-backed rationale for this root cause (required).",
                            required = true)
                    String rationale,
            @ToolParam(
                            description = "Confidence: TENTATIVE, PROBABLE, or CONFIRMED (default PROBABLE).",
                            required = false)
                    String confidence) {
        IncidentRootCause resolved = IncidentRootCause.require(cause);
        String trimmedRationale = rationale == null ? "" : rationale.trim();
        RootCauseConfidence resolvedConfidence =
                confidence == null || confidence.isBlank()
                        ? RootCauseConfidence.PROBABLE
                        : RootCauseConfidence.require(confidence);
        state.setRootCauseClassification(resolved, trimmedRationale, resolvedConfidence);
        return "{cause=%s, label=%s, confidence=%s, rationale=%s}"
                .formatted(
                        resolved.name(),
                        resolved.getLabel(),
                        resolvedConfidence.name(),
                        trimmedRationale);
    }
}
