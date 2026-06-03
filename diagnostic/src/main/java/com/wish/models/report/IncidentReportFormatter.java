package com.wish.models.report;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class IncidentReportFormatter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private IncidentReportFormatter() {}

    static String toMarkdown(IncidentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagnostic incident report\n\n");
        sb.append("## Metadata\n\n");
        sb.append("- **Completed**: ").append(TIMESTAMP.format(report.completedAt())).append('\n');
        sb.append("- **Context ID**: ").append(nullToDash(report.contextId())).append('\n');
        sb.append("- **Session ID**: ").append(nullToDash(report.sessionId())).append('\n');
        sb.append("- **Final stage**: ").append(nullToDash(report.finalStage())).append("\n\n");

        sb.append("## User request\n\n");
        sb.append(plainText(report.userRequest())).append("\n\n");

        sb.append("## Classification\n\n");
        sb.append("### Problem type\n\n");
        appendCategory(sb, report.problemType(), report.problemTypeLabel(), report.problemSummary());

        sb.append("### Root cause\n\n");
        if (report.rootCause() != null && !report.rootCause().isBlank()) {
            sb.append("- **Category**: ")
                    .append(report.rootCause())
                    .append(" (")
                    .append(nullToDash(report.rootCauseLabel()))
                    .append(")\n");
            if (report.rootCauseConfidence() != null && !report.rootCauseConfidence().isBlank()) {
                sb.append("- **Confidence**: ").append(report.rootCauseConfidence()).append('\n');
            }
            if (report.rootCauseRationale() != null && !report.rootCauseRationale().isBlank()) {
                sb.append("- **Rationale**: ").append(report.rootCauseRationale().trim()).append('\n');
            }
        } else {
            sb.append("(not assigned)\n");
        }
        sb.append('\n');

        sb.append("## Stage findings\n\n");
        if (report.stageFindings() == null || report.stageFindings().isEmpty()) {
            sb.append("(none)\n\n");
        } else {
            for (IncidentReport.StageFindingEntry entry : report.stageFindings()) {
                sb.append("### ").append(nullToDash(entry.stage())).append("\n\n");
                sb.append(nestedMarkdown(entry.finding(), 2)).append("\n\n");
            }
        }

        sb.append("## Investigation summary\n\n");
        sb.append(nestedMarkdown(report.investigationSummary())).append("\n\n");

        sb.append("## Run result\n\n");
        sb.append(nestedMarkdown(report.runResult())).append('\n');
        return sb.toString();
    }

    private static void appendCategory(
            StringBuilder sb, String code, String label, String summary) {
        if (code == null || code.isBlank()) {
            sb.append("(not assigned)\n");
            return;
        }
        sb.append("- **Category**: ").append(code).append(" (").append(nullToDash(label)).append(")\n");
        if (summary != null && !summary.isBlank()) {
            sb.append("- **Summary**: ").append(summary.trim()).append('\n');
        }
    }

    /** Single-line / plain fields (user request). */
    private static String plainText(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        return unescapeFinding(text).replace('\n', ' ').trim();
    }

    /**
     * Stage findings and summaries are markdown from the model; normalize escapes and demote headings so
     * {@code ##} inside {@code ### CLASSIFY} nests correctly.
     */
    static String nestedMarkdown(String text) {
        return nestedMarkdown(text, 1);
    }

    static String nestedMarkdown(String text, int headingDemote) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        return demoteHeadings(unescapeFinding(text), headingDemote);
    }

    static String unescapeFinding(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Bump heading levels so nested content under {@code ### Stage} does not compete with report {@code ##}.
     */
    static String demoteHeadings(String markdown, int extraLevels) {
        if (extraLevels <= 0 || markdown.isBlank()) {
            return markdown;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            int hashes = countLeadingHashes(line);
            if (hashes > 0 && (line.length() == hashes || line.charAt(hashes) == ' ')) {
                int newHashes = Math.min(6, hashes + extraLevels);
                sb.append("#".repeat(newHashes));
                if (line.length() > hashes) {
                    sb.append(line.substring(hashes));
                }
                sb.append('\n');
            } else {
                sb.append(line).append('\n');
            }
        }
        String result = sb.toString();
        return result.endsWith("\n") ? result.substring(0, result.length() - 1) : result;
    }

    private static int countLeadingHashes(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') {
            count++;
        }
        return count;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
