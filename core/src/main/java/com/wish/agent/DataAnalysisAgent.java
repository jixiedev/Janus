package com.wish.agent;

import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import com.wish.tools.TerminateTool;
import com.wish.tools.analysis.ChartLlmConfig;
import com.wish.tools.analysis.DataAnalysisPythonExecuteTool;
import com.wish.tools.analysis.DataVisualizationTool;
import com.wish.tools.analysis.VisualizationPrepareTool;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class DataAnalysisAgent extends ToolCallAgent {
    private static final String NAME = "DataAnalysis";
    private static final String DESCRIPTION = "An analytical agent that utilizes python and data visualization tools to solve diverse data analysis tasks";
    private static final String SYSTEM_PROMPT = """
            Role: You are an AI agent for data analysis and visualization tasks.

            Instruction: Use your tools step by step to complete the user's analysis request.

            Context: The workspace directory is: %s. Read and write files only within this workspace.

            Output: Generate an analysis conclusion report when the task is complete.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Instruction: Based on user needs, break down the problem and use different tools step by step to solve it.

            Rules:
            - Each step, select the most appropriate tool proactively (ONLY ONE).
            - After using each tool, clearly explain the execution results and suggest the next steps.
            - When a tool returns an error, review and fix it before continuing.
            """;

    private static final String SESSION_SUMMARY_SYSTEM_PROMPT = """
            Instruction: Summarize one completed data-analysis task for long-term session memory.

            Input: User request and agent trace will follow in the user message.

            Output: Write a concise summary with these sections:
            - Current task goal
            - Data sources and operations performed
            - Key findings and metrics
            - Outputs produced (charts, files, paths when known)
            - Unresolved issues or suggested next steps, if any

            Rules: Use the same language as the user's request. Do not include raw logs or stack traces.
            """;

    public DataAnalysisAgent(Supplier<LLMChatClient> chatClientSupplier, int maxSteps, Path workspaceRoot) {
        this(chatClientSupplier, maxSteps, workspaceRoot, List.of());
    }

    private static List<Object> builtinTools(Path workspaceRoot, Supplier<LLMChatClient> chatClientSupplier) {
        ChartLlmConfig chartLlm = ChartLlmConfig.from(chatClientSupplier.get());
        return List.of(
                new DataAnalysisPythonExecuteTool(),
                new VisualizationPrepareTool(),
                new DataVisualizationTool(workspaceRoot, chartLlm),
                new TerminateTool());
    }

    public DataAnalysisAgent(
            Supplier<LLMChatClient> chatClientSupplier, int maxSteps, Path workspaceRoot, List<Object> mcpTools) {
        this(chatClientSupplier, maxSteps, workspaceRoot, mcpTools, List.of());
    }

    public DataAnalysisAgent(
            Supplier<LLMChatClient> chatClientSupplier,
            int maxSteps,
            Path workspaceRoot,
            List<Object> mcpTools,
            List<Object> skillsTools) {
        super(
                NAME,
                DESCRIPTION,
                SYSTEM_PROMPT.formatted(workspaceRoot.toAbsolutePath().normalize()),
                NEXT_STEP_PROMPT,
                SESSION_SUMMARY_SYSTEM_PROMPT,
                chatClientSupplier,
                maxSteps,
                mcpTools,
                skillsTools,
                builtinTools(workspaceRoot, chatClientSupplier));
    }
}
