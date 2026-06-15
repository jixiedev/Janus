package com.wish.prompt;

import com.wish.model.DiagnosticContext;
import com.wish.model.state.State;
import com.wish.models.context.Context;
import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticPromptsTest {

    @Test
    void sharedInvestigationBackgroundIncludesRequestProgressAndPriorSession() {
        State state = new State();
        state.addFinding(Stage.CLASSIFY, "Problem: pipeline failure.");
        String background =
                DiagnosticPrompts.sharedInvestigationBackground(
                        state, "check DAG", "User: prior\n\nAssistant: upstream delay.");

        assertTrue(background.contains("Context — investigation time (UTC):"));
        assertTrue(background.indexOf("Context — user request:") < background.indexOf("Context — prior session:"));
        assertTrue(background.indexOf("Context — prior session:") < background.indexOf("Context — structured progress:"));
        assertTrue(background.contains("check DAG"));
        assertTrue(background.contains("upstream delay"));
        assertTrue(background.contains("Problem: pipeline failure"));
    }

    @Test
    void reportPersistHintRequiresIncidentTimeRules() {
        String hint = DiagnosticPrompts.reportPersistHint(false, "/tmp/incident-report.md");

        assertTrue(hint.contains("Incident time (required"));
        assertTrue(hint.contains("Last occurrence"));
        assertTrue(hint.contains("User scope first"));
        assertTrue(hint.contains("NOT when the investigation started"));
    }

    @Test
    void sharedInvestigationBackgroundUsesFixedInvestigationClock() {
        State state = new State();
        String background =
                DiagnosticPrompts.sharedInvestigationBackground(
                        state,
                        "最近两天失败",
                        "",
                        Instant.parse("2026-06-10T09:00:00Z"));

        assertTrue(background.contains("Context — investigation time (UTC): 2026-06-10T09:00:00Z"));
    }

    @Test
    void routeTurnPromptOmitsSharedBackground() {
        State state = new State();
        state.addFinding(Stage.CLASSIFY, "Problem: pipeline failure.");
        String prompt = DiagnosticPrompts.routeTurnPrompt(state);

        assertTrue(prompt.contains("Instruction: Choose the next investigation stage"));
        assertTrue(prompt.contains("last stage outcome: after stage Classify"));
        assertFalse(prompt.contains("Context — user request:"));
        assertFalse(prompt.contains("Context — structured progress:"));
    }

    @Test
    void routeAndStageShareBackgroundAtSamePointInLoop() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        context.currentState.addFinding(Stage.COLLECT, "found logs");

        String routeBackground = context.formatSharedInvestigationBackground();
        context.beginStage();
        String stageBackground = context.getCurrentStageContext().getSharedInvestigationBackground();

        assertTrue(routeBackground.contains("found logs"));
        assertEqualsNormalized(routeBackground, stageBackground);
        context.endStage();
    }

    @Test
    void prepareRouteStageContextUsesSharedBackgroundThenRouteTurn() {
        var chatMemory = org.springframework.ai.chat.memory.MessageWindowChatMemory.builder().build();
        String sessionKey = com.wish.models.session.DiagnosticSession.SESSION_KEY_PREFIX + "route-prefix-test";
        chatMemory.add(sessionKey, new UserMessage("first question"));
        chatMemory.add(sessionKey, new org.springframework.ai.chat.messages.AssistantMessage("first answer"));

        DiagnosticContext context = DiagnosticContext.forRun("ctx-1", "route-prefix-test");
        context.bootstrapMainSession(
                DiagnosticPrompts.DIAGNOSTIC_SYSTEM, chatMemory.get(sessionKey), "second question");

        var routeContext = context.prepareRouteStageContext();
        var messages = routeContext.getAllMessages();

        assertTrue(messages.get(1).getText().contains("Context — user request:"));
        assertTrue(messages.get(1).getText().contains("second question"));
        assertTrue(messages.get(1).getText().contains("Context — structured progress:"));
        assertTrue(messages.get(2).getText().contains("Instruction: Choose the next investigation stage"));
        assertTrue(messages.stream().anyMatch(m -> m.getText().contains("first question")));
    }

    @Test
    void prepareRouteStageContextResetsPriorRoutingTurn() {
        DiagnosticContext context = DiagnosticContext.createEphemeral("incident");
        Context routeContext = context.prepareRouteStageContext();
        routeContext.addMemory(new UserMessage("stale routing turn"));
        assertTrue(routeContext.getAllMessages().size() > 2);

        routeContext = context.prepareRouteStageContext();
        var messages = routeContext.getAllMessages();

        assertFalse(messages.stream().anyMatch(m -> m.getText().contains("stale routing turn")));
        assertTrue(messages.get(1).getText().contains("incident"));
    }

    private static void assertEqualsNormalized(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected same shared background for route and stage");
        }
    }
}
