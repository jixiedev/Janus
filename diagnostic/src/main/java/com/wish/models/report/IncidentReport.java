package com.wish.models.report;

import java.time.Instant;
import java.util.List;

/** Structured incident outcome for one diagnostic prompt run. */
public record IncidentReport(
        String contextId,
        String sessionId,
        Instant completedAt,
        String userRequest,
        String finalStage,
        String problemType,
        String problemTypeLabel,
        String problemSummary,
        String rootCause,
        String rootCauseLabel,
        String rootCauseConfidence,
        String rootCauseRationale,
        List<StageFindingEntry> stageFindings,
        String investigationSummary,
        String runResult) {

    public record StageFindingEntry(String stage, String finding) {}
}
