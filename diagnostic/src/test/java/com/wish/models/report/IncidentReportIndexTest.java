package com.wish.models.report;

import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReportIndexTest {

    @Test
    void upsertCreatesAndUpdatesIndexEntry(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.createEphemeral("DAG daily_sales failed at 3am");
            Instant incidentOccurred = Instant.parse("2026-06-08T03:00:00Z");
            context.currentState.setIncidentClassification(
                    IncidentCategory.JOB_EXECUTION, "Failed Airflow run", incidentOccurred);
            context.currentState.setRootCauseClassification(
                    IncidentRootCause.ORCHESTRATION, "Sensor blocked", RootCauseConfidence.PROBABLE);

            IncidentReportIndex index = new IncidentReportIndex();
            IncidentReportStore store = new IncidentReportStore(index);
            Path reportPath =
                    reportsRoot.resolve("ephemeral").resolve("dag-daily-sales-failed-at-3am").resolve("incident-report.md");

            store.persist(context, "create", "# Report v1");
            assertTrue(Files.exists(index.resolveIndexPath()));

            var entries = index.readAll();
            assertEquals(1, entries.size());
            IncidentReportIndexEntry first = entries.getFirst();
            assertEquals("JOB_EXECUTION", first.incidentClassification());
            assertEquals("ORCHESTRATION", first.incidentTag());
            assertEquals(reportPath.toAbsolutePath().normalize().toString(), first.reportPath());
            assertEquals(incidentOccurred, first.incidentTime());
            assertTrue(first.reportTime().isBefore(Instant.now().plusSeconds(1)));

            Thread.sleep(5);
            store.persist(context, "update", "# Report v2");
            entries = index.readAll();
            assertEquals(1, entries.size());
            assertTrue(entries.getFirst().reportTime().isAfter(first.reportTime()));
            assertEquals("# Report v2", Files.readString(reportPath).trim());
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }

    @Test
    void tagFallsBackToSessionIdWhenRootCauseUnset(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.forRun("context:test", "incident-42");
            context.bootstrapMainSession("system", java.util.List.of(), "pipeline failed");
            context.currentState.setIncidentClassification(
                    IncidentCategory.DATA_ISSUE, "bad rows", Instant.parse("2026-06-07T00:00:00Z"));

            IncidentReportIndex index = new IncidentReportIndex();
            new IncidentReportStore(index).persist(context, "create", "# Report");

            IncidentReportIndexEntry entry = index.readAll().getFirst();
            assertEquals("DATA_ISSUE", entry.incidentClassification());
            assertEquals("incident-42", entry.incidentTag());
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }
}
