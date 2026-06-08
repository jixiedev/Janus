package com.wish.loop;

import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import com.wish.model.stage.Stage;
import com.wish.models.context.BaseUserContext;
import com.wish.prompt.DiagnosticPrompts;
import com.wish.support.AgentRunOutputResolver;
import com.wish.tools.StageFindingTool;
import com.wish.tools.TerminateTool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Tool-call agent for a single diagnostic stage. Findings are delivered via {@link StageFindingTool},
 * not create_chat_completion.
 */
public class StageToolCallAgent extends ToolCallAgent {

    private static final String STAGE_SESSION_SUMMARY = """
            Instruction: Summarize one completed diagnostic stage for the main investigation record.

            Input: Stage trace will follow in the user message.

            Output: Write a concise summary covering:
            - Stage name
            - Evidence gathered
            - Conclusions
            - Open questions

            Rules: Use the same language as the incident report.
            """;

    private static final List<Object> STAGE_BUILTIN_TOOLS = List.of(new StageFindingTool(), new TerminateTool());

    public static StageToolCallAgent forStage(
            Stage stage, Supplier<LLMChatClient> chatClientSupplier, List<Object> stageTools) {
        List<Object> tools = new ArrayList<>();
        if (stageTools != null) {
            tools.addAll(stageTools);
        }
        return new StageToolCallAgent(
                "diagnostic-" + stage.name(),
                "Diagnostic stage agent: " + stage.getName(),
                stage.getSystemPrompt(),
                stage.nextStepPrompt(),
                STAGE_SESSION_SUMMARY,
                chatClientSupplier,
                stage.getMaxSteps(),
                tools,
                STAGE_BUILTIN_TOOLS);
    }

    protected StageToolCallAgent(
            String name,
            String description,
            String systemPrompt,
            String nextStepPrompt,
            String sessionSummarySystemPrompt,
            Supplier<LLMChatClient> chatClientSupplier,
            int maxSteps,
            List<Object> mcpTools,
            List<Object> builtinTools) {
        super(
                name,
                description,
                systemPrompt,
                nextStepPrompt,
                sessionSummarySystemPrompt,
                chatClientSupplier,
                maxSteps,
                mcpTools,
                builtinTools);
    }

    @Override
    protected String extractUserVisibleOutput(BaseUserContext userContext, List<String> stepResults) {
        String fromTool = AgentRunOutputResolver.findLastToolResponse(
                userContext.getPromptMessages(), DiagnosticPrompts.SUBMIT_STAGE_FINDING_TOOL);
        if (fromTool != null && !fromTool.isBlank()) {
            return fromTool.trim();
        }
        return super.extractUserVisibleOutput(userContext, stepResults);
    }

    public String directSessionResultTool() {
        return DiagnosticPrompts.SUBMIT_STAGE_FINDING_TOOL;
    }
}
