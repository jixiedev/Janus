package com.wish.model;

import com.wish.models.context.BaseUserContext;
import com.wish.models.context.PromptPrefixLayout;
import com.wish.models.context.ToolCallUserContext;
import com.wish.model.stage.Stage;
import com.wish.model.stage.StageContext;
import com.wish.model.state.State;
import com.wish.prompt.DiagnosticPrompts;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * One investigation run with strict context boundaries:
 * <ul>
 *   <li><b>Main session</b> ({@link #mainSession}) — incident + stage findings; hydrated from {@link Session} on multi-turn</li>
 *   <li><b>Router</b> ({@link #routeChatMemory}) — isolated routing turns</li>
 *   <li><b>Stage</b> ({@link #stageChatMemory}) — isolated per stage; cleared in {@link #endStage()}</li>
 * </ul>
 * Cross-boundary: {@link #snapshotMainSession()} at stage start; {@link #contributeStageFinding} at stage end.
 * Multi-turn: prior turns are assembled into {@link #mainSession} via {@link #bootstrapMainSession}.
 */
@Slf4j
public class DiagnosticContext {
    private static final String ROUTE_CONVERSATION_PREFIX = "diagnostic-route-";
    private static final String STAGE_CONVERSATION_PREFIX = "diagnostic-stage-";

    @Getter
    private final String contextId;
    @Getter
    private final String sessionId;

    @Getter
    private String rawRequest;

    private final ChatMemory routeChatMemory;
    private final ChatMemory stageChatMemory;

    public final State currentState;

    private final List<Message> mainSession = new ArrayList<>();

    /** Index in {@link #mainSession} where this prompt run starts (after session hydration). */
    @Getter
    private int promptRunStartIndex = 0;

    private final LinkedList<StageContribution> stageContributions = new LinkedList<>();

    @Getter
    private StageContext currentStageContext;

    @Getter
    @Setter
    private String runResult = "";

    private String currentStageConversationId;
    private BaseUserContext routeStageContext;

    private DiagnosticContext(String contextId, String sessionId, State currentState) {
        this.contextId = contextId;
        this.sessionId = sessionId;
        this.currentState = currentState;
        this.routeChatMemory = MessageWindowChatMemory.builder().build();
        this.stageChatMemory = MessageWindowChatMemory.builder().build();
    }

    public static DiagnosticContext forRun(String contextId, String sessionId) {
        return new DiagnosticContext(contextId, sessionId, new State());
    }

    /** Single-turn investigation; no session hydration. */
    public static DiagnosticContext createEphemeral(String rawRequest) {
        String contextId = UUID.randomUUID().toString();
        DiagnosticContext context = new DiagnosticContext(contextId, null, new State());
        context.bootstrapMainSession(DiagnosticPrompts.DIAGNOSTIC_SYSTEM, List.of(), rawRequest);
        return context;
    }

    /**
     * Builds the main-session prefix via {@link PromptPrefixLayout}.
     * Optimization: system and current incident precede prior session summaries.
     *
     * @param rawRequest shell/API incident text (formatted for the model internally)
     * @return number of bound session messages (excluding framing)
     */
    public int bootstrapMainSession(String systemPrompt, List<Message> sessionMessages, String rawRequest) {
        this.rawRequest = rawRequest == null ? "" : rawRequest.trim();
        List<Message> bound =
                sessionMessages == null || sessionMessages.isEmpty() ? List.of() : List.copyOf(sessionMessages);
        String userRequest = DiagnosticPrompts.initialIncidentPrompt(this.rawRequest);
        List<Message> prefix = PromptPrefixLayout.build(systemPrompt, bound, userRequest);
        mainSession.clear();
        mainSession.addAll(prefix);
        promptRunStartIndex = prefix.size();
        return bound.size();
    }

    public List<Message> getMainSession() {
        return List.copyOf(mainSession);
    }

    /**
     * Messages produced during this prompt run only (for {@link com.wish.models.session.PromptRunSummarizer}).
     */
    public List<Message> getPromptMessagesForSummary() {
        if (promptRunStartIndex >= mainSession.size()) {
            return List.of();
        }
        return List.copyOf(mainSession.subList(promptRunStartIndex, mainSession.size()));
    }

    public List<StageContribution> getStageContributions() {
        return List.copyOf(stageContributions);
    }

    public String snapshotMainSession() {
        return DiagnosticPrompts.backgroundFromMessages(mainSession);
    }

    public void beginStage() {
        String backgroundSnapshot = snapshotMainSession();
        currentStageConversationId =
                STAGE_CONVERSATION_PREFIX + contextId + "-" + currentState.getStageCnt();
        ToolCallUserContext stageUserContext =
                new ToolCallUserContext(currentStageConversationId, sessionId, stageChatMemory);
        this.currentStageContext = new StageContext(
                stageUserContext,
                currentState.getCurrentStage(),
                currentState.getCurrentStageThought(),
                backgroundSnapshot);
    }

    public void endStage() {
        if (currentStageContext == null) {
            return;
        }
        try {
            Stage stage = currentState.getCurrentStage();
            if (stage == Stage.CLASSIFY && currentState.getIncidentCategory() == null) {
                log.warn("CLASSIFY stage ended without incident category on context {}", contextId);
            }
            String finding = currentStageContext.getStageFinding();
            if (finding != null && !finding.isBlank()) {
                contributeStageFinding(stage, finding);
            }
        } finally {
            if (currentStageConversationId != null) {
                stageChatMemory.clear(currentStageConversationId);
            }
            currentStageConversationId = null;
            currentStageContext = null;
        }
    }

    public void contributeStageFinding(Stage stage, String finding) {
        if (finding == null || finding.isBlank()) {
            return;
        }
        String trimmed = finding.trim();
        currentState.addFinding(stage, trimmed);
        mainSession.add(new UserMessage(DiagnosticPrompts.stageFindingMessage(stage, trimmed)));
        stageContributions.add(new StageContribution(stage, trimmed));
    }

    public boolean shouldContinue() {
        return currentState.getCurrentStage().shouldContinue();
    }

    public BaseUserContext getRouteStageContext() {
        if (routeStageContext == null) {
            routeStageContext = new BaseUserContext(ROUTE_CONVERSATION_PREFIX + contextId, sessionId, routeChatMemory);
            routeStageContext.initializePromptPrefix(DiagnosticPrompts.ROUTER_SYSTEM, "");
        }
        return routeStageContext;
    }

    public void applyRoutingFallback() {
        Stage current = currentState.getCurrentStage();
        Stage fallback =
                switch (current) {
                    case INITIAL -> Stage.CLASSIFY;
                    case CLASSIFY -> Stage.COLLECT;
                    default -> Stage.ANALYZE;
                };
        currentState.enterIntoStage(fallback);
        log.info(
                "Routing fallback: {} -> {} (contextId={})",
                current.name(),
                fallback.name(),
                contextId);
        if (currentState.getCurrentStageThought() == null
                || currentState.getCurrentStageThought().isBlank()) {
            currentState.setCurrentStageThought(
                    "Auto-selected " + fallback.getName() + " (router tool not called)");
        }
    }

    /** User-visible outcome of this investigation run. */
    public String buildRunResult() {
        if (!stageContributions.isEmpty()) {
            StageContribution last = stageContributions.getLast();
            if (last.finding() != null && !last.finding().isBlank()) {
                return last.finding();
            }
        }
        return currentState.desc();
    }

    /** Clears per-run router partition; stage partitions are cleared in {@link #endStage()}. */
    public void cleanupRun() {
        if (routeStageContext != null) {
            routeChatMemory.clear(routeStageContext.getConversation());
            routeStageContext = null;
        }
    }

    public record StageContribution(Stage stage, String finding) {}
}
