package com.wish.models.report;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReportFormatterTest {

    @Test
    void nestedMarkdownUnescapesAndDemotesHeadings() {
        String raw =
                "\"## Collect summary\\n\\n| col | val |\\n|---|---|\\n| a | 1 |\\n\\n**Bold** text\"";
        String normalized = IncidentReportFormatter.nestedMarkdown(raw, 2);
        assertFalse(normalized.startsWith("\""));
        assertTrue(normalized.contains("\n| col |"));
        assertTrue(normalized.startsWith("#### Collect summary"));
        assertFalse(normalized.contains("\\n"));
    }

    @Test
    void stageFindingsSectionRendersNestedMarkdown() {
        IncidentReport report =
                new IncidentReport(
                        "ctx-1",
                        null,
                        Instant.parse("2026-06-03T08:45:54Z"),
                        "DAG failed",
                        "TERMINATE",
                        "JOB_EXECUTION",
                        "Job execution",
                        "pipeline failed",
                        null,
                        null,
                        null,
                        null,
                        List.of(new IncidentReport.StageFindingEntry(
                                "COLLECT", "## Evidence\\n\\n- item one\\n- item two")),
                        "summary",
                        "result");

        String md = IncidentReportFormatter.toMarkdown(report);
        assertTrue(md.contains("### COLLECT"));
        assertTrue(md.contains("#### Evidence"));
        assertTrue(md.contains("- item one"));
        assertFalse(md.contains("\"## Evidence"));
    }
}
