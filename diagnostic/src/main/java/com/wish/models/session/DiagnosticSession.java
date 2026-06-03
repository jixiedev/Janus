package com.wish.models.session;

import com.wish.model.DiagnosticContext;
import com.wish.models.context.DiagnosticRunContext;
import com.wish.models.report.IncidentReportArchiver;
import com.wish.prompt.DiagnosticPrompts;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Multi-turn diagnostic investigations, aligned with core {@link Session}:
 * <ul>
 *   <li>{@code session:{id}} — summarized prior user requests and investigation outcomes</li>
 *   <li>Each {@link #beginPrompt} — fresh {@link State}, hydrated main session, ephemeral router/stage memories</li>
 *   <li>{@link #endPrompt} — merges this run into session memory; clears per-run partitions</li>
 * </ul>
 *
 * <pre>{@code
 * DiagnosticSession session = new DiagnosticSession(sessionId, chatMemory, chatModel);
 * DiagnosticRunContext run = session.beginPrompt("follow-up: check Airflow DAG x");
 * try {
 *     String outcome = agent.process(run.getDiagnosticContext());
 *     run.setRunResult(outcome);
 *     session.endPrompt(outcome);
 * } catch (RuntimeException e) {
 *     session.abortPrompt();
 *     throw e;
 * }
 * }</pre>
 */
public class DiagnosticSession extends Session<DiagnosticRunContext> {

    private final PromptRunSummarizer summarizer;
    private final IncidentReportArchiver reportArchiver;

    public DiagnosticSession(String sessionId, ChatMemory chatMemory, ChatModel chatModel) {
        super(sessionId, chatMemory, true);
        this.summarizer =
                new PromptRunSummarizer(
                        chatModel,
                        DiagnosticPrompts.SESSION_SUMMARY_SYSTEM,
                        DiagnosticPrompts.SUBMIT_STAGE_FINDING_TOOL);
        this.reportArchiver = new IncidentReportArchiver(chatModel);
    }

    /** Heuristic summarization only (tests). */
    DiagnosticSession(String sessionId, ChatMemory chatMemory) {
        this(sessionId, chatMemory, null);
    }

    @Override
    protected DiagnosticRunContext createContext(String contextId) {
        return new DiagnosticRunContext(contextId, getSessionId(), getChatMemory());
    }

    @Override
    protected void initializeContext(DiagnosticRunContext runContext, String request) {
        DiagnosticContext diagnosticContext =
                DiagnosticContext.forRun(runContext.getConversation(), getSessionId());
        if (isPersistent()) {
            diagnosticContext.hydratePriorSession(getSessionMessages());
        }
        diagnosticContext.beginCurrentIncident(request);
        runContext.setDiagnosticContext(diagnosticContext);
    }

    @Override
    protected void extractToSessionMemory(DiagnosticRunContext runContext) {
        DiagnosticContext diagnosticContext = runContext.getDiagnosticContext();
        if (diagnosticContext == null) {
            return;
        }
        String request = runContext.getPromptRequest();
        if (request == null || request.isBlank()) {
            return;
        }
        String runResult = runContext.getRunResult();
        if (runResult == null || runResult.isBlank()) {
            runResult = diagnosticContext.buildRunResult();
        }
        PromptRunSummary summary =
                summarizer.summarize(request, diagnosticContext.getPromptMessagesForSummary(), runResult);
        if (summary.agentResult() == null || summary.agentResult().isBlank()) {
            return;
        }
        String sessionKey = getSessionMemoryKey();
        getChatMemory().add(sessionKey, new UserMessage(summary.userRequest()));
        getChatMemory().add(sessionKey, new AssistantMessage(summary.agentResult()));
    }

    @Override
    public synchronized void endPrompt(String runResult) {
        DiagnosticContext diagnosticContext =
                getCurrentContext() != null ? getCurrentContext().getDiagnosticContext() : null;
        try {
            super.endPrompt(runResult);
        } finally {
            if (diagnosticContext != null) {
                reportArchiver.archiveRun(diagnosticContext, runResult);
                diagnosticContext.cleanupRun();
            }
        }
    }

    /** Writes a session-closure marker under the session reports directory. */
    public void archiveSessionClosed() {
        reportArchiver.archiveSessionClosed(getSessionId());
    }

    @Override
    public synchronized void abortPrompt() {
        DiagnosticContext diagnosticContext =
                getCurrentContext() != null ? getCurrentContext().getDiagnosticContext() : null;
        try {
            super.abortPrompt();
        } finally {
            if (diagnosticContext != null) {
                diagnosticContext.cleanupRun();
            }
        }
    }
}
