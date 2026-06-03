package com.wish.support;

import com.wish.model.DiagnosticContext;
import com.wish.model.incident.IncidentCategory;
import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticProgressLoggerTest {

    @Test
    void formatStateBlockIncludesSummaryAndProblemType() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("DAG failed");
        context.currentState.setIncidentClassification(IncidentCategory.JOB_EXECUTION, "pipeline failed");
        context.contributeStageFinding(Stage.CLASSIFY, "classified");

        String block = DiagnosticProgressLogger.formatStateBlock(context.currentState);
        assertTrue(block.contains("currentStage="));
        assertTrue(block.contains("JOB_EXECUTION"));
        assertTrue(block.contains("CLASSIFY"));
    }
}
