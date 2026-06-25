package com.wish.models.report;

import com.wish.model.DiagnosticContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Persists one human-readable incident report per diagnostic session (or ephemeral slug).
 * Create/update is driven by the agent via {@link com.wish.tools.IncidentReportTool}, not the service layer.
 */
@Slf4j
public class IncidentReportStore {

    public static final String REPORT_FILENAME = "incident-report.md";

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final IncidentReportIndex reportIndex;

    public IncidentReportStore() {
        this(new IncidentReportIndex());
    }

    IncidentReportStore(IncidentReportIndex reportIndex) {
        this.reportIndex = reportIndex;
    }

    public boolean reportExists(String sessionId, String rawRequest) {
        return Files.isRegularFile(resolveReportPath(sessionId, rawRequest));
    }

    public Path resolveReportPath(String sessionId, String rawRequest) {
        Path directory = IncidentReportPaths.resolveSessionDirectory(sessionId, rawRequest);
        return directory.resolve(REPORT_FILENAME);
    }

    public String resolveDisplayPath(String sessionId, String rawRequest) {
        return resolveReportPath(sessionId, rawRequest).toString();
    }

    /**
     * @param action {@code create} or {@code update}
     * @param markdown agent-authored report body; blank falls back to {@link IncidentReportBuilder}
     */
    public String persist(DiagnosticContext context, String action, String markdown) {
        if (context == null) {
            return "Cannot persist report: missing diagnostic context.";
        }
        String normalizedAction = action == null ? "" : action.trim().toLowerCase();
        String content =
                markdown == null || markdown.isBlank()
                        ? IncidentReportBuilder.build(context)
                        : markdown.trim();
        Path path = resolveReportPath(context.getSessionId(), context.getRawRequest());
        try {
            IncidentReportPaths.ensureDirectory(path.getParent());
            boolean exists = Files.isRegularFile(path);
            if ("update".equals(normalizedAction)) {
                writeReportAndIndex(context, path, content);
                log.info("Incident report {}: {}", exists ? "updated" : "created", path);
                return exists
                        ? "Updated incident report at " + path
                        : "Created incident report at " + path + " (no prior file existed)";
            }
            if ("create".equals(normalizedAction)) {
                if (exists) {
                    return "Incident report already exists at "
                            + path
                            + ". Call persist_incident_report with action=update to revise it.";
                }
                writeReportAndIndex(context, path, content);
                log.info("Incident report created: {}", path);
                return "Created incident report at " + path;
            }
            return "Unknown action '"
                    + action
                    + "'. Use action=create for a new report or action=update to revise an existing one.";
        } catch (IOException e) {
            log.warn("Failed to persist incident report at {}: {}", path, e.getMessage());
            return "Failed to persist incident report: " + e.getMessage();
        }
    }

    private void writeReportAndIndex(DiagnosticContext context, Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        reportIndex.upsert(IncidentReportIndexEntry.from(context, path, Instant.now()));
    }

    /** Marks a multi-turn session as closed under its reports directory. */
    public Optional<Path> archiveSessionClosed(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            Path directory = IncidentReportPaths.resolveSessionDirectory(sessionId, null);
            if (!Files.isDirectory(directory)) {
                return Optional.empty();
            }
            IncidentReportPaths.ensureDirectory(directory);
            String baseName = FILE_STAMP.format(Instant.now()) + "_session_closed";
            Path marker = directory.resolve(baseName + ".md");
            Path reportPath = directory.resolve(REPORT_FILENAME);
            String body =
                    """
                    # Diagnostic session closed

                    - **Session ID**: %s
                    - **Closed at**: %s
                    - **Reports directory**: %s
                    - **Incident report**: %s
                    """
                            .formatted(
                                    sessionId.trim(),
                                    Instant.now(),
                                    directory,
                                    Files.isRegularFile(reportPath) ? reportPath : "(none)");
            Files.writeString(marker, body, StandardCharsets.UTF_8);
            log.info("Diagnostic session closure marker: {}", marker);
            return Optional.of(marker);
        } catch (IOException e) {
            log.warn("Failed to write session closure marker for {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }
}
