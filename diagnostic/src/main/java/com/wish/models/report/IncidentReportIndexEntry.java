package com.wish.models.report;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.state.State;

import java.nio.file.Path;
import java.time.Instant;

/** One row in the global incident-report index. */
@JsonPropertyOrder({
    "reportTime",
    "incidentTime",
    "incidentClassification",
    "incidentTag",
    "reportPath"
})
public record IncidentReportIndexEntry(
        Instant reportTime,
        Instant incidentTime,
        String incidentClassification,
        String incidentTag,
        String reportPath) {

    static IncidentReportIndexEntry from(DiagnosticContext context, Path reportPath, Instant reportTime) {
        State state = context.currentState;
        Instant incidentTime = state.getIncidentOccurredAt();
        return new IncidentReportIndexEntry(
                reportTime,
                incidentTime,
                classificationOf(state.getIncidentCategory()),
                tagOf(context, state),
                reportPath.toAbsolutePath().normalize().toString());
    }

    private static String classificationOf(IncidentCategory category) {
        return category == null ? "" : category.name();
    }

    /**
     * Root-cause taxonomy when assigned; otherwise session id or request slug for lookup.
     */
    private static String tagOf(DiagnosticContext context, State state) {
        IncidentRootCause rootCause = state.getRootCause();
        if (rootCause != null) {
            return rootCause.name();
        }
        String sessionId = context.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.trim();
        }
        return IncidentReportPaths.slugFromRequest(context.getRawRequest());
    }
}
