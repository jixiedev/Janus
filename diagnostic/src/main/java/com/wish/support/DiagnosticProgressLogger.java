package com.wish.support;

import com.wish.llm.LLMChatClient;
import com.wish.model.DiagnosticContext;
import com.wish.model.stage.Stage;
import com.wish.model.state.State;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Structured INFO logs for diagnostic stage transitions and {@link State} snapshots. */
@Slf4j
public final class DiagnosticProgressLogger {

    private static final int MAX_FINDING_LOG_CHARS = 400;
    private static final int MAX_THOUGHT_LOG_CHARS = 300;
    private static final int MAX_REQUEST_LOG_CHARS = 200;

    private DiagnosticProgressLogger() {}

    public static void logInvestigationStart(DiagnosticContext context) {
        logMultiline(
                """
                ========== Diagnostic investigation START ==========
                contextId=%s
                sessionId=%s
                request=%s
                %s
                ====================================================
                """
                        .formatted(
                                context.getContextId(),
                                nullToDash(context.getSessionId()),
                                truncate(context.getRawRequest(), MAX_REQUEST_LOG_CHARS),
                                formatStateBlock(context.currentState)));
    }

    public static void logRoutingStart(DiagnosticContext context) {
        State state = context.currentState;
        logMultiline(
                """
                ---------- Router: choosing next stage ----------
                contextId=%s afterStage=%s stageVisits=%s
                routingThought=%s
                %s
                -------------------------------------------------
                """
                        .formatted(
                                context.getContextId(),
                                state.getCurrentStage().name(),
                                state.getStageCnt(),
                                truncate(state.getCurrentStageThought(), MAX_THOUGHT_LOG_CHARS),
                                formatStateBlock(state)));
    }

    public static void logRoutingResult(DiagnosticContext context, boolean usedFallback) {
        State state = context.currentState;
        logMultiline(
                """
                ---------- Router: selected stage ----------
                contextId=%s nextStage=%s fallback=%s stageVisits=%s
                routingThought=%s
                %s
                -----------------------------------------
                """
                        .formatted(
                                context.getContextId(),
                                state.getCurrentStage().name(),
                                usedFallback,
                                state.getStageCnt(),
                                truncate(state.getCurrentStageThought(), MAX_THOUGHT_LOG_CHARS),
                                formatStateBlock(state)));
    }

    public static void logStageEnter(DiagnosticContext context, Stage stage, List<Object> stageTools) {
        logMultiline(
                """
                >>> ENTER stage %s (visit #%s) — contextId=%s
                tools(%s): %s
                routingThought=%s
                %s
                """
                        .formatted(
                                stage.name(),
                                context.currentState.getStageCnt(),
                                context.getContextId(),
                                stageTools.size(),
                                formatToolNames(stageTools),
                                truncate(context.currentState.getCurrentStageThought(), MAX_THOUGHT_LOG_CHARS),
                                formatStateBlock(context.currentState)));
    }

    public static void logStageExit(DiagnosticContext context, Stage stage, String stageFinding) {
        String findingLog =
                stageFinding == null || stageFinding.isBlank()
                        ? "(no submit_stage_finding captured)"
                        : truncate(stageFinding, MAX_FINDING_LOG_CHARS);
        logMultiline(
                """
                <<< EXIT stage %s — contextId=%s
                stageFinding=%s
                %s
                """
                        .formatted(
                                stage.name(),
                                context.getContextId(),
                                findingLog,
                                formatStateBlock(context.currentState)));
    }

    public static void logInvestigationEnd(DiagnosticContext context, String runResult) {
        logMultiline(
                """
                ========== Diagnostic investigation END ==========
                contextId=%s finalStage=%s continue=%s
                runResultPreview=%s
                %s
                ==================================================
                """
                        .formatted(
                                context.getContextId(),
                                context.currentState.getCurrentStage().name(),
                                context.shouldContinue(),
                                truncate(runResult, MAX_FINDING_LOG_CHARS),
                                formatStateBlock(context.currentState)));
    }

    /**
     * Log a fully formatted message. Uses a single {@code {}} SLF4J arg so literal braces in state text are safe.
     */
    private static void logMultiline(String message) {
        log.info("{}", message.trim());
    }

    /** Compact + detailed state for logs (also used by router prompts via {@link State#desc()}). */
    public static String formatStateBlock(State state) {
        if (state == null) {
            return "[diagnostic state: null]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[diagnostic state summary] ")
                .append(state.summaryLine())
                .append('\n');
        sb.append(state.desc());
        return sb.toString().trim();
    }

    static String formatToolNames(List<Object> tools) {
        if (tools == null || tools.isEmpty()) {
            return "(none)";
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object tool : tools) {
            String name = LLMChatClient.resolveToolName(tool);
            if (name != null && !name.isBlank()) {
                names.add(name);
            } else {
                names.add(tool.getClass().getSimpleName());
            }
        }
        return String.join(", ", new ArrayList<>(names));
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String trimmed = value.trim().replace('\n', ' ');
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "...";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
