package com.wish.loop;

import com.wish.llm.LLMChatClient;
import com.wish.model.DiagnosticContext;
import com.wish.model.stage.Stage;
import com.wish.models.context.BaseUserContext;
import com.wish.prompt.DiagnosticPrompts;
import com.wish.support.DiagnosticProgressLogger;
import com.wish.support.ToolCallingPromptSupport;
import com.wish.tools.IncidentClassificationTool;
import com.wish.tools.RootCauseClassificationTool;
import com.wish.tools.StageRouter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.List;
import java.util.function.Supplier;

/**
 * Multi-stage incident diagnostic loop. Per-stage tools come from {@link Stage#resolveTools(List)} only.
 */
@Slf4j
public class DiagnosticAgent {

    private final Supplier<LLMChatClient> stageLlmClientSupplier;
    private final LLMChatClient primaryLLMChatClient;
    private final ToolCallingManager toolCallingManager;
    private final List<Object> registeredMcpTools;

    public DiagnosticAgent(ChatModel chatModel, List<Object> registeredMcpTools, ToolCallingManager toolCallingManager) {
        this.registeredMcpTools = registeredMcpTools == null ? List.of() : List.copyOf(registeredMcpTools);
        this.toolCallingManager = toolCallingManager;
        this.primaryLLMChatClient = new LLMChatClient(chatModel, List.of());
        this.stageLlmClientSupplier = () -> new LLMChatClient(chatModel, List.of());
    }

    public String process(DiagnosticContext context) {
        DiagnosticProgressLogger.logInvestigationStart(context);
        do {
            routeStage(context);
            handleStage(context);
        } while (context.shouldContinue());
        String result = context.buildRunResult();
        context.setRunResult(result);
        DiagnosticProgressLogger.logInvestigationEnd(context, result);
        return result;
    }

    private void routeStage(DiagnosticContext context) {
        DiagnosticProgressLogger.logRoutingStart(context);
        String routeStagePrompt = buildRouteStagePrompt(context);
        BaseUserContext routeStageContext = context.getRouteStageContext();
        routeStageContext.addMemory(new UserMessage(routeStagePrompt));

        int stageCntBefore = context.currentState.getStageCnt();
        StageRouter router = new StageRouter(context.currentState);
        Pair<ChatResponse, Prompt> response =
                primaryLLMChatClient.askWithTools(routeStageContext, List.of(), List.of(router));
        AssistantMessage assistantMessage = response.getKey().getResult().getOutput();
        routeStageContext.addMemory(assistantMessage);

        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls() != null
                ? assistantMessage.getToolCalls()
                : List.of();

        if (!toolCalls.isEmpty()) {
            String thought = assistantMessage.getText();
            ToolExecutionResult result = toolCallingManager.executeToolCalls(
                    ToolCallingPromptSupport.forToolExecution(response.getRight()), response.getLeft());
            routeStageContext.replaceMemory(result.conversationHistory());
            if (context.currentState.getStageCnt() > stageCntBefore) {
                context.currentState.setCurrentStageThought(thought == null ? "" : thought.trim());
            }
        }

        boolean usedFallback = context.currentState.getStageCnt() <= stageCntBefore;
        if (usedFallback) {
            log.warn("Router did not select a stage; applying fallback");
            context.applyRoutingFallback();
        }
        DiagnosticProgressLogger.logRoutingResult(context, usedFallback);
    }

    private String buildRouteStagePrompt(DiagnosticContext context) {
        return DiagnosticPrompts.routeStageUserPrompt(context.currentState, context.snapshotMainSession());
    }

    private void handleStage(DiagnosticContext context) {
        Stage stage = context.currentState.getCurrentStage();
        context.beginStage();
        List<Object> stageTools = new java.util.ArrayList<>();
        try {
            stageTools.addAll(stage.resolveTools(registeredMcpTools));
            if (stage == Stage.CLASSIFY) {
                stageTools.add(new IncidentClassificationTool(context.currentState));
            }
            if (stage.allowsRootCauseClassification()) {
                stageTools.add(new RootCauseClassificationTool(context.currentState));
            }
            DiagnosticProgressLogger.logStageEnter(context, stage, stageTools);
            StageProcessor stageProcessor =
                    new StageProcessor(context.getCurrentStageContext(), stage, stageLlmClientSupplier, stageTools);
            stageProcessor.execute();
        } finally {
            String finding =
                    context.getCurrentStageContext() != null
                            ? context.getCurrentStageContext().getStageFinding()
                            : "";
            context.endStage();
            DiagnosticProgressLogger.logStageExit(context, stage, finding);
        }
    }
}
