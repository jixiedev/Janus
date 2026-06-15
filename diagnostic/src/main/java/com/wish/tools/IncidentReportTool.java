package com.wish.tools;

import com.wish.model.DiagnosticContext;
import com.wish.models.report.IncidentReportStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.wish.prompt.DiagnosticPrompts.INCIDENT_REPORT_MARKDOWN_PARAM_DESCRIPTION;
import static com.wish.prompt.DiagnosticPrompts.PERSIST_INCIDENT_REPORT_TOOL;

/**
 * Agent-driven incident report persistence. Available in TERMINATE when reports are enabled.
 * The agent chooses {@code create} vs {@code update}; blank markdown uses a structured state draft.
 */
public class IncidentReportTool {

    private final DiagnosticContext context;
    private final IncidentReportStore store;

    public IncidentReportTool(DiagnosticContext context, IncidentReportStore store) {
        this.context = context;
        this.store = store;
    }

    @Tool(
            name = PERSIST_INCIDENT_REPORT_TOOL,
            description =
                    "Persist the incident report for this investigation session. "
                            + "Use action=create for the first report, action=update to revise an existing report "
                            + "(e.g. follow-up conversations). Markdown must include incident occurrence time per "
                            + "report rules; omit markdown only for a structured state draft.")
    public String persistIncidentReport(
            @ToolParam(
                            description = "create: write the first report; update: replace the existing session report",
                            required = true)
                    String action,
            @ToolParam(description = INCIDENT_REPORT_MARKDOWN_PARAM_DESCRIPTION, required = false)
                    String markdown) {
        return store.persist(context, action, markdown);
    }
}
