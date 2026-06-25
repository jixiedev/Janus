package com.wish.model.stage;

import com.wish.model.DiagnosticContext;
import com.wish.tools.IncidentClassificationTool;
import com.wish.tools.IncidentLastOccurredTool;
import com.wish.tools.IncidentReportTool;
import com.wish.tools.RootCauseClassificationTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves per-stage tools and stage-scoped prompt hints for a single investigation run.
 * Stateless builtins and MCP policy come from {@link Stage}; context-bound tools are added here.
 */
public final class StageToolResolver {

    private StageToolResolver() {}

    /**
     * Full tool list for one stage execution: {@link Stage#resolveTools(List)} plus investigation tools
     * that require {@link DiagnosticContext} or {@link com.wish.model.state.State}.
     */
    public static List<Object> resolveTools(
            Stage stage, DiagnosticContext context, List<Object> registeredMcpTools) {
        List<Object> tools = new ArrayList<>(stage.resolveTools(registeredMcpTools));
        if (stage == Stage.CLASSIFY) {
            tools.add(new IncidentClassificationTool(context.currentState));
        }
        if (stage.allowsRootCauseClassification()) {
            tools.add(new RootCauseClassificationTool(context.currentState));
            tools.add(new IncidentLastOccurredTool(context.currentState));
        }
        if (stage == Stage.TERMINATE && context.isReportingEnabled()) {
            tools.add(new IncidentReportTool(context, context.getReportStore()));
        }
        return List.copyOf(tools);
    }

    /** Applies stage-specific variable prompt blocks on {@link StageContext} before execution. */
    public static void configureStageContext(Stage stage, DiagnosticContext context, StageContext stageContext) {
        if (stageContext == null || context == null) {
            return;
        }
        if (stage == Stage.TERMINATE && context.isReportingEnabled()) {
            stageContext.setReportPersistHint(context.reportPersistHint());
        }
    }
}
