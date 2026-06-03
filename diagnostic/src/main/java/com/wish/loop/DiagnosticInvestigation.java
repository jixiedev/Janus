package com.wish.loop;

import com.wish.model.DiagnosticContext;
import com.wish.models.context.DiagnosticRunContext;
import com.wish.models.report.IncidentReportArchiver;
import com.wish.models.session.DiagnosticSession;
import com.wish.support.AgentRunSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for ephemeral or session-backed diagnostic runs (mirrors shell {@code ToolCallService}).
 */
@Slf4j
public class DiagnosticInvestigation {

    private final DiagnosticAgent agent;
    private final ChatModel chatModel;
    private final ChatMemory sharedChatMemory;
    private final Map<String, DiagnosticSession> sessions = new ConcurrentHashMap<>();

    public DiagnosticInvestigation(DiagnosticAgent agent, ChatModel chatModel) {
        this(agent, chatModel, MessageWindowChatMemory.builder().build());
    }

    public DiagnosticInvestigation(DiagnosticAgent agent, ChatModel chatModel, ChatMemory sharedChatMemory) {
        this.agent = agent;
        this.chatModel = chatModel;
        this.sharedChatMemory = sharedChatMemory;
    }

    public String run(String prompt) {
        return run(prompt, null);
    }

    /**
     * @param sessionId when set, reuses {@link DiagnosticSession} and hydrates prior turns into the main session
     */
    public String run(String prompt, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return runEphemeral(prompt);
        }
        return runPersistent(prompt, sessionId.trim());
    }

    private String runEphemeral(String prompt) {
        DiagnosticContext context = DiagnosticContext.createEphemeral(prompt);
        IncidentReportArchiver archiver = new IncidentReportArchiver(chatModel);
        try {
            String result = agent.process(context);
            context.setRunResult(result);
            archiver.archiveRun(context, result);
            return result;
        } finally {
            context.cleanupRun();
        }
    }

    private String runPersistent(String prompt, String sessionId) {
        DiagnosticSession session =
                sessions.computeIfAbsent(sessionId, id -> {
                    log.info("New diagnostic session '{}'", id);
                    return new DiagnosticSession(id, sharedChatMemory, chatModel);
                });
        DiagnosticRunContext runContext = session.beginPrompt(prompt);
        try {
            String result = AgentRunSupport.runWithTokenLogging(
                    runContext, () -> agent.process(runContext.getDiagnosticContext()));
            runContext.setRunResult(result);
            session.endPrompt(result);
            return result;
        } catch (RuntimeException e) {
            session.abortPrompt();
            throw e;
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        String normalized = sessionId.trim();
        DiagnosticSession removed = sessions.remove(normalized);
        if (removed != null) {
            removed.archiveSessionClosed();
            removed.clear();
        } else {
            sharedChatMemory.clear(DiagnosticSession.SESSION_KEY_PREFIX + normalized);
        }
    }
}
