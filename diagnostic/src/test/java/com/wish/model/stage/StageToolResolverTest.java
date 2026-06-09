package com.wish.model.stage;

import com.wish.model.DiagnosticContext;
import com.wish.models.report.IncidentReportStore;
import com.wish.prompt.DiagnosticPrompts;
import com.wish.tools.IncidentClassificationTool;
import com.wish.tools.IncidentReportTool;
import com.wish.tools.RootCauseClassificationTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageToolResolverTest {

    @Test
    void classifyStageIncludesIncidentClassificationTool() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        List<Object> tools = StageToolResolver.resolveTools(Stage.CLASSIFY, context, List.of());

        assertEquals(1, tools.size());
        assertInstanceOf(IncidentClassificationTool.class, tools.getFirst());
    }

    @Test
    void collectStageIncludesRootCauseTool() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        List<Object> tools = StageToolResolver.resolveTools(Stage.COLLECT, context, List.of());

        assertTrue(tools.stream().anyMatch(RootCauseClassificationTool.class::isInstance));
    }

    @Test
    void terminateWithReportingIncludesPersistTool() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        context.setReportStore(new IncidentReportStore());

        List<Object> tools = StageToolResolver.resolveTools(Stage.TERMINATE, context, List.of());

        assertTrue(tools.stream().anyMatch(IncidentReportTool.class::isInstance));
    }

    @Test
    void terminateWithoutReportingOmitsPersistTool() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");

        List<Object> tools = StageToolResolver.resolveTools(Stage.TERMINATE, context, List.of());

        assertTrue(tools.stream().noneMatch(IncidentReportTool.class::isInstance));
    }

    @Test
    void configureStageContextSetsReportHintOnTerminate() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        context.setReportStore(new IncidentReportStore());
        StageContext stageContext =
                new StageContext(
                        new com.wish.models.context.ToolCallUserContext("stage-1", null, null),
                        Stage.TERMINATE,
                        "thought",
                        "background");

        StageToolResolver.configureStageContext(Stage.TERMINATE, context, stageContext);

        assertTrue(stageContext.getReportPersistHint().contains(DiagnosticPrompts.PERSIST_INCIDENT_REPORT_TOOL));
    }
}
