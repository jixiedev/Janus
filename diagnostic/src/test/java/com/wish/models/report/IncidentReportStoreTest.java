package com.wish.models.report;

import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReportStoreTest {

    @Test
    void persistCreateWritesSingleHumanReadableFile(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.createEphemeral("DAG daily_sales failed at 3am");
            context.currentState.setIncidentClassification(
                    IncidentCategory.JOB_EXECUTION, "User reports failed Airflow run");
            context.currentState.setRootCauseClassification(
                    IncidentRootCause.ORCHESTRATION, "Upstream sensor never cleared", RootCauseConfidence.PROBABLE);
            context.contributeStageFinding(Stage.TERMINATE, "Root cause: blocked sensor on upstream table.");

            IncidentReportStore store = new IncidentReportStore();
            String result = store.persist(context, "create", "");

            Path reportPath = reportsRoot.resolve("ephemeral").resolve("dag-daily-sales-failed-at-3am").resolve("incident-report.md");
            assertTrue(Files.exists(reportPath));
            assertTrue(result.contains("Created incident report"));
            String content = Files.readString(reportPath);
            assertTrue(content.contains("JOB_EXECUTION"));
            assertTrue(content.contains("ORCHESTRATION"));
            assertTrue(content.contains("daily_sales"));
            assertTrue(content.contains("## Conclusion"));
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }

    @Test
    void persistUpdateReplacesExistingSessionReport(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.forRun("context:test", "incident-1");
            context.bootstrapMainSession("system", java.util.List.of(), "first incident");

            IncidentReportStore store = new IncidentReportStore();
            store.persist(context, "create", "# First version");

            String updateResult = store.persist(context, "update", "# Updated version");
            Path reportPath = reportsRoot.resolve("sessions").resolve("incident-1").resolve("incident-report.md");

            assertEquals("# Updated version", Files.readString(reportPath).trim());
            assertTrue(updateResult.contains("Updated incident report"));
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }

    @Test
    void persistCreateRejectsDuplicate(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.forRun("context:test", "incident-2");
            context.bootstrapMainSession("system", java.util.List.of(), "incident");

            IncidentReportStore store = new IncidentReportStore();
            store.persist(context, "create", "# Report");
            String second = store.persist(context, "create", "# Again");

            assertTrue(second.contains("action=update"));
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }

    @Test
    void archiveSessionClosedWritesMarker(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            Path sessionDir = IncidentReportPaths.resolveSessionDirectory("incident-1", null);
            IncidentReportPaths.ensureDirectory(sessionDir);
            Files.writeString(sessionDir.resolve("incident-report.md"), "prior run");

            IncidentReportStore store = new IncidentReportStore();
            var marker = store.archiveSessionClosed("incident-1");

            assertTrue(marker.isPresent());
            assertTrue(Files.readString(marker.get()).contains("session closed"));
            assertTrue(Files.readString(marker.get()).contains("incident-report.md"));
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }
}
