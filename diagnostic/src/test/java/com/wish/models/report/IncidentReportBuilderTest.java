package com.wish.models.report;

import com.wish.model.DiagnosticContext;
import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReportBuilderTest {

    @Test
    void buildAvoidsDuplicateSections() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("pipeline failed");
        context.contributeStageFinding(Stage.COLLECT, "Found error in logs.");
        context.contributeStageFinding(Stage.TERMINATE, "Final: upstream delay.");

        String markdown = IncidentReportBuilder.build(context);

        assertTrue(markdown.contains("## Stage findings"));
        assertTrue(markdown.contains("## Conclusion"));
        assertTrue(markdown.contains("Final: upstream delay."));
        assertFalse(markdown.contains("## Investigation summary"));
        assertFalse(markdown.contains("## Run result"));
    }
}
