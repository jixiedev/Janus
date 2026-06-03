package com.wish.models.session;

import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticSessionTest {

    @Test
    void secondPromptHydratesPriorSessionSummary() {
        String sessionId = "diag-test-1";
        var chatMemory = MessageWindowChatMemory.builder().build();
        DiagnosticSession session = new DiagnosticSession(sessionId, chatMemory);

        var run1 = session.beginPrompt("incident A: pipeline failed");
        run1.getDiagnosticContext().contributeStageFinding(Stage.TERMINATE, "Root cause: upstream delay.");
        run1.setRunResult(run1.getDiagnosticContext().buildRunResult());
        session.endPrompt(run1.getRunResult());

        var run2 = session.beginPrompt("follow-up: any impact on downstream?");
        var dc2 = run2.getDiagnosticContext();
        String snapshot = dc2.snapshotMainSession();
        assertTrue(snapshot.contains("incident A") || snapshot.contains("upstream delay"));
        assertEquals(4, dc2.getPromptRunStartIndex());
        session.abortPrompt();
    }

    @Test
    void beginPromptHydratesExistingSessionMemory() {
        String sessionId = "diag-test-2";
        var chatMemory = MessageWindowChatMemory.builder().build();
        String sessionKey = DiagnosticSession.SESSION_KEY_PREFIX + sessionId;
        chatMemory.add(sessionKey, new UserMessage("first question"));
        chatMemory.add(sessionKey, new AssistantMessage("first answer"));

        DiagnosticSession session = new DiagnosticSession(sessionId, chatMemory);
        var run = session.beginPrompt("second question");
        assertTrue(run.getDiagnosticContext().snapshotMainSession().contains("first question"));
        assertTrue(run.getDiagnosticContext().snapshotMainSession().contains("second question"));
        session.abortPrompt();
    }
}
