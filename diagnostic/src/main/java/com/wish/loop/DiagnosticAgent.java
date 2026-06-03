package com.wish.loop;

import com.wish.agent.base.BaseAgent;
import com.wish.llm.LLMChatClient;
import com.wish.model.DiagnosticContext;
import com.wish.models.context.BaseUserContext;
import com.wish.model.stage.Stage;
import com.wish.support.ToolCallingPromptSupport;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.List;
import java.util.function.Supplier;

/*
system prompt(Diagnostic Context system prompt)

choose a stage(route context)
  -> You are in xxx stage(Stage Context system prompt)
  -> A new Stage Context(sc user prompt)
    -> tool call loops(sc)
    -> extract the findings(sc)
    -> add findings(dc)

...... more stages
until:
TERMINATE/ERROR stage => do a conclusion
 */

public class DiagnosticAgent {

    private static final String systemPrompt = "you are an expert, ready to investigate an incident.";

    private final ChatMemory chatMemory;
    private final BaseAgent baseAgent;
    private final Supplier<LLMChatClient> llmChatClientSupplier;
    private final LLMChatClient primaryLLMChatClient;

    private final ToolCallingManager toolCallingManager;
    public DiagnosticAgent(ChatMemory chatMemory, BaseAgent baseAgent, Supplier<LLMChatClient> llmChatClientSupplier, ToolCallingManager toolCallingManager) {
        this.chatMemory = chatMemory;
        this.baseAgent = baseAgent;
        this.llmChatClientSupplier = llmChatClientSupplier;
        this.toolCallingManager = toolCallingManager;
        this.primaryLLMChatClient = llmChatClientSupplier.get();
    }

    /*
    in this step, limit the scope of tools to StageRouter.
     */
    private static final String stageRouterPrompt = """
            Please choose a stage to proceed on.
            The last stage you selected is %s, which finds that %s.
            Current status of this incident is %s.
            """;


    public void process(DiagnosticContext context) {
        while (context.shouldContinue()) {
            this.routeStage(context);
            handleStage(context);

        }


    }

    private void routeStage(DiagnosticContext context) {
        String routeStagePrompt = buildRouteStagePrompt();
        BaseUserContext routeStageContext = context.getRouteStageContext();
        routeStageContext.ensureSystemPrompt(systemPrompt);
        routeStageContext.addMemory(new UserMessage(routeStagePrompt));
        //pass in StageRouter tool
        Pair<ChatResponse, Prompt> response = primaryLLMChatClient.askWithTools(routeStageContext, List.of(), List.of());
        AssistantMessage assistantMessage = response.getKey().getResult().getOutput();
        routeStageContext.addMemory(assistantMessage);
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls() != null
                ? assistantMessage.getToolCalls()
                : List.of();

        if (!toolCalls.isEmpty()) {
            String thought = assistantMessage.getText();
            int stageCntBefore = context.currentState.getStageCnt();
            ToolExecutionResult result = toolCallingManager.executeToolCalls(
                    ToolCallingPromptSupport.forToolExecution(response.getRight()), response.getLeft());
            routeStageContext.replaceMemory(result.conversationHistory());
            //todo how to ensure a stage is selected?
            int stageCntAfter = context.currentState.getStageCnt();
            if(stageCntAfter == stageCntBefore + 1) {
                context.currentState.setCurrentStageThought(thought);
            }
        }
        //by default, get into the analysis stage
        context.currentState.enterIntoStage(Stage.ANALYZE);
        context.currentState.setCurrentStageThought("keep analyze");
    }

    public String buildRouteStagePrompt() {
        return "";
    }

    private void handleStage(DiagnosticContext context) {
        context.beginStage();
        StageProcessor stageProcessor = new StageProcessor(context.getCurrentStageContext(), context.currentState.currentStage, llmChatClientSupplier);
        stageProcessor.execute();
        context.endStage();
    }
}
