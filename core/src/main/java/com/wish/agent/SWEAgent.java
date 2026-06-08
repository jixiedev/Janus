package com.wish.agent;

import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import com.wish.tools.BashTool;
import com.wish.tools.StrReplaceEditor;
import com.wish.tools.TerminateTool;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class SWEAgent extends ToolCallAgent {
    private static final int VIEW_WINDOW_LINES = 4;

    private static final String NAME = "SWE";
    private static final String DESCRIPTION =
            "an autonomous AI programmer that interacts directly with the computer to solve tasks.";

    private static final String SYSTEM_PROMPT = """
            Role: You are an autonomous AI programmer working directly in the command line.

            Context: The special interface shows %d lines of a file at a time. In addition to bash commands, \
            you can use specific tools to navigate and edit files. Tool calls invoke commands.

            Context: The workspace directory is: %s

            Rules:
            - THE EDIT COMMAND REQUIRES PROPER INDENTATION. If you'd like to add the line '        print(x)' \
            you must fully write that out, with all those spaces before the code. Incorrect indentation will fail.
            - Include exactly one tool call per response, then wait for the shell response before continuing.
            - Do not issue two commands at once; submit the first tool call, wait, then submit the second.
            - The environment does NOT support interactive session commands (e.g. python, vim).

            Output format:
            (Open file: <path>)
            (Current directory: <cwd>)
            bash-$

            First include a brief thought about your next action, then exactly one tool call.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Instruction: Decide the single best action for this turn.

            Rules:
            - If the user request is already fully satisfied (analysis/review delivered, edits done, \
            or no further tool output is needed), call terminate(status="success") only. \
            Do not output assistant text in that turn.
            - If you still need information or must change the workspace, use exactly one tool call \
            (bash, str_replace_editor, Read, Skill, etc.). Do not bundle multiple tools.
            - Do not restate, paraphrase, or "summarize again" an answer already given in a prior step. \
            One user-visible conclusion is enough.
            - Do not produce two consecutive assistant-only replies. After a complete answer, \
            the next turn MUST be terminate(status="success").
            - Match the user's language in tool arguments and in any brief thought before a tool call.
            """;

    private static final String SESSION_SUMMARY_SYSTEM_PROMPT = """
            Instruction: Summarize one completed software-engineering task for long-term session memory.

            Input: User request and agent trace will follow in the user message.

            Output: Write a concise summary with these sections:
            - Current task goal
            - Steps completed
            - Key findings
            - Unresolved issues
            - Next-step plan

            Rules: Use the same language as the user's request. Do not include raw logs or stack traces.
            """;

    public SWEAgent(Supplier<LLMChatClient> chatClientSupplier, int maxSteps, Path workspaceRoot) {
        this(chatClientSupplier, maxSteps, workspaceRoot, List.of());
    }

    private static List<Object> builtinTools(Path workspaceRoot) {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        return List.of(new BashTool(workspace), new StrReplaceEditor(), new TerminateTool());
    }

    public SWEAgent(
            Supplier<LLMChatClient> chatClientSupplier, int maxSteps, Path workspaceRoot, List<Object> mcpTools) {
        this(chatClientSupplier, maxSteps, workspaceRoot, mcpTools, List.of());
    }

    public SWEAgent(
            Supplier<LLMChatClient> chatClientSupplier,
            int maxSteps,
            Path workspaceRoot,
            List<Object> mcpTools,
            List<Object> skillsTools) {
        super(
                NAME,
                DESCRIPTION,
                SYSTEM_PROMPT.formatted(
                        VIEW_WINDOW_LINES, workspaceRoot.toAbsolutePath().normalize()),
                NEXT_STEP_PROMPT,
                SESSION_SUMMARY_SYSTEM_PROMPT,
                chatClientSupplier,
                maxSteps,
                mcpTools,
                skillsTools,
                builtinTools(workspaceRoot));
    }
}
