package com.wish.models.report;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the on-disk root for diagnostic incident reports.
 *
 * <p>Precedence: system property {@value #PROPERTY} → env {@value #ENV} → default under user home.
 */
public final class IncidentReportPaths {

    public static final String PROPERTY = "janus.diagnostic.reports.dir";
    public static final String ENV = "JANUS_DIAGNOSTIC_REPORTS_DIR";
    public static final String DEFAULT_SUBDIR = ".janus/diagnostic-reports";

    private IncidentReportPaths() {}

    public static Path resolveRoot() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENV);
        }
        Path root =
                configured == null || configured.isBlank()
                        ? Path.of(System.getProperty("user.home", ".")).resolve(DEFAULT_SUBDIR)
                        : Path.of(configured.trim());
        return root.toAbsolutePath().normalize();
    }

    /**
     * One directory per session or ephemeral investigation; holds a single {@code incident-report.md}.
     *
     * <ul>
     *   <li>Persistent session ({@code -c id}): {@code sessions/{sessionId}/}</li>
     *   <li>Ephemeral: {@code ephemeral/{request-slug}/}</li>
     * </ul>
     */
    public static Path resolveSessionDirectory(String sessionId, String rawRequest) {
        Path root = resolveRoot();
        if (sessionId != null && !sessionId.isBlank()) {
            return root.resolve("sessions").resolve(sanitizePathSegment(sessionId.trim()));
        }
        return root.resolve("ephemeral").resolve(slugFromRequest(rawRequest));
    }

    /** @deprecated Use {@link #resolveSessionDirectory(String, String)} */
    @Deprecated
    public static Path resolveRunDirectory(String sessionId) {
        return resolveSessionDirectory(sessionId, null);
    }

    static String slugFromRequest(String request) {
        if (request == null || request.isBlank()) {
            return "untitled";
        }
        String slug =
                request.trim()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-+)|(-+$)", "");
        if (slug.isBlank()) {
            return "untitled";
        }
        return slug.length() > 48 ? slug.substring(0, 48) : slug;
    }

    static String sanitizePathSegment(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "unknown";
        }
        return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
    }

    public static void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create incident reports directory: " + directory, e);
        }
    }
}
