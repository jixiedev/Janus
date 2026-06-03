package com.wish.models.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.stage.Stage;
import com.wish.model.state.State;
import com.wish.models.session.PromptRunSummarizer;
import com.wish.prompt.DiagnosticPrompts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists a structured incident summary when a diagnostic prompt run completes, and optional session closure markers.
 */
@Slf4j
public class IncidentReportArchiver {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final PromptRunSummarizer summarizer;

    public IncidentReportArchiver(ChatModel chatModel) {
        this.summarizer =
                new PromptRunSummarizer(
                        chatModel,
                        DiagnosticPrompts.INCIDENT_REPORT_SUMMARY_SYSTEM,
                        DiagnosticPrompts.SUBMIT_STAGE_FINDING_TOOL);
    }

    /** Heuristic report only (tests). */
    IncidentReportArchiver() {
        this(null);
    }

    /**
     * Writes markdown + JSON for one completed investigation run.
     *
     * @return written markdown path, or empty if persistence failed
     */
    public java.util.Optional<Path> archiveRun(DiagnosticContext context, String runResult) {
        if (context == null) {
            return java.util.Optional.empty();
        }
        String effectiveResult = runResult == null || runResult.isBlank() ? context.getRunResult() : runResult;
        if (effectiveResult == null || effectiveResult.isBlank()) {
            effectiveResult = context.buildRunResult();
        }
        try {
            IncidentReport report = buildReport(context, effectiveResult);
            Path directory = IncidentReportPaths.resolveRunDirectory(context.getSessionId());
            IncidentReportPaths.ensureDirectory(directory);
            String baseName = FILE_STAMP.format(report.completedAt()) + "_" + shortId(context.getContextId());
            Path markdownPath = directory.resolve(baseName + ".md");
            Path jsonPath = directory.resolve(baseName + ".json");
            Files.writeString(markdownPath, IncidentReportFormatter.toMarkdown(report), StandardCharsets.UTF_8);
            JSON.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), report);
            log.info("Incident report saved: {}", markdownPath);
            return java.util.Optional.of(markdownPath);
        } catch (Exception e) {
            log.warn("Failed to save incident report for context {}: {}", context.getContextId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Marks a multi-turn session as closed under {@code sessions/{sessionId}/}. */
    public java.util.Optional<Path> archiveSessionClosed(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            Path directory = IncidentReportPaths.resolveRunDirectory(sessionId);
            if (!Files.isDirectory(directory)) {
                return java.util.Optional.empty();
            }
            IncidentReportPaths.ensureDirectory(directory);
            String baseName = FILE_STAMP.format(Instant.now()) + "_session_closed";
            Path marker = directory.resolve(baseName + ".md");
            String body =
                    """
                    # Diagnostic session closed

                    - **Session ID**: %s
                    - **Closed at**: %s
                    - **Reports directory**: %s

                    Individual incident reports for this session are stored alongside this file.
                    """
                            .formatted(sessionId.trim(), Instant.now(), directory);
            Files.writeString(marker, body, StandardCharsets.UTF_8);
            log.info("Diagnostic session closure marker: {}", marker);
            return java.util.Optional.of(marker);
        } catch (IOException e) {
            log.warn("Failed to write session closure marker for {}: {}", sessionId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private IncidentReport buildReport(DiagnosticContext context, String runResult) {
        State state = context.currentState;
        String request = context.getRawRequest() == null ? "" : context.getRawRequest().trim();
        String investigationSummary =
                summarizer
                        .summarize(request, context.getPromptMessagesForSummary(), runResult)
                        .agentResult();

        List<IncidentReport.StageFindingEntry> findings = new ArrayList<>();
        for (Pair<Stage, String> entry : state.getStageFindings()) {
            findings.add(
                    new IncidentReport.StageFindingEntry(
                            entry.getLeft().name(), entry.getRight() == null ? "" : entry.getRight()));
        }

        IncidentCategory problem = state.getIncidentCategory();
        IncidentRootCause cause = state.getRootCause();
        RootCauseConfidence confidence = state.getRootCauseConfidence();

        return new IncidentReport(
                context.getContextId(),
                context.getSessionId(),
                Instant.now(),
                request,
                state.getCurrentStage().name(),
                problem == null ? null : problem.name(),
                problem == null ? null : problem.getLabel(),
                state.getIncidentCategoryRationale(),
                cause == null ? null : cause.name(),
                cause == null ? null : cause.getLabel(),
                confidence == null ? null : confidence.name(),
                state.getRootCauseRationale(),
                List.copyOf(findings),
                investigationSummary == null ? "" : investigationSummary.trim(),
                runResult == null ? "" : runResult.trim());
    }

    private static String shortId(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            return "run";
        }
        String id = contextId.trim();
        return id.length() <= 12 ? id : id.substring(0, 12);
    }
}
