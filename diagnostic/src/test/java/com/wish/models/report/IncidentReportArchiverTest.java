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

import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReportArchiverTest {

    @Test
    void archiveRunWritesMarkdownAndJson(@TempDir Path reportsRoot) {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            DiagnosticContext context = DiagnosticContext.createEphemeral("DAG daily_sales failed at 3am");
            context.currentState.setIncidentClassification(
                    IncidentCategory.JOB_EXECUTION, "User reports failed Airflow run");
            context.currentState.setRootCauseClassification(
                    IncidentRootCause.ORCHESTRATION, "Upstream sensor never cleared", RootCauseConfidence.PROBABLE);
            context.contributeStageFinding(Stage.TERMINATE, "Root cause: blocked sensor on upstream table.");

            IncidentReportArchiver archiver = new IncidentReportArchiver();
            var path = archiver.archiveRun(context, "Investigation complete.");

            assertTrue(path.isPresent());
            Path md = path.get();
            assertTrue(Files.exists(md));
            assertTrue(Files.exists(md.resolveSibling(md.getFileName().toString().replace(".md", ".json"))));
            String content = Files.readString(md);
            assertTrue(content.contains("JOB_EXECUTION"));
            assertTrue(content.contains("ORCHESTRATION"));
            assertTrue(content.contains("daily_sales"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }

    @Test
    void archiveSessionClosedWritesMarker(@TempDir Path reportsRoot) throws Exception {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsRoot.toString());
        try {
            Path sessionDir = IncidentReportPaths.resolveRunDirectory("incident-1");
            IncidentReportPaths.ensureDirectory(sessionDir);
            Files.writeString(sessionDir.resolve("placeholder.md"), "prior run");

            IncidentReportArchiver archiver = new IncidentReportArchiver();
            var marker = archiver.archiveSessionClosed("incident-1");

            assertTrue(marker.isPresent());
            assertTrue(Files.readString(marker.get()).contains("session closed"));
        } finally {
            System.clearProperty(IncidentReportPaths.PROPERTY);
        }
    }
}
