package com.wish.model.stage;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic workflow stage. Routing via {@link com.wish.tools.StageRouter};
 * Static per-stage config lives here ({@link StageBuiltin} + {@link McpToolPolicy}).
 * Runtime tool assembly and stage hints: {@link StageToolResolver}.
 */
@Getter
public enum Stage {
    INITIAL(
            "Initial",
            List.of(),
            McpToolPolicy.NONE,
            1,
            false,
            """
                    Role: INITIAL stage handoff agent.
                    Instruction: Immediately hand off investigation startup.
                    Output: Call submit_stage_finding stating that investigation is starting, \
                    then terminate(status="success").
                    """),
    CLASSIFY(
            "Classify",
            List.of(),
            McpToolPolicy.NONE,
            10,
            false,
            """
                    Role: CLASSIFY stage agent in an incident investigation.

                    Instruction: Classify the **problem type** (what is wrong or what the user reports) — not the root cause.

                    Rules:
                    - Do not assign categories such as infrastructure fault, misconfiguration, or vendor blame; \
                    those belong in later ANALYZE / VALIDATE stages after evidence.
                    - Read the user request and background.
                    - Choose exactly one problem type from the allowed enum.

                    Output: Call classify_incident with the enum name and a short rationale tied to reported symptoms. \
                    When classification is recorded, call submit_stage_finding summarizing the problem type only, \
                    then call terminate(status="success").

                    Allowed problem types:
                    """
                    + com.wish.model.incident.IncidentCategory.catalogForPrompt()),
    COLLECT(
            "Collect",
            List.of(StageBuiltin.QUERY_WAREHOUSE, StageBuiltin.S3, StageBuiltin.KUBERNETES),
            McpToolPolicy.ALL,
            30,
            false,
            """
                    Role: COLLECT stage agent in an incident investigation.

                    Instruction: Gather concrete evidence: timestamps, job IDs, error messages, affected tables/pipelines, \
                    and recent changes.

                    Rules: Use available tools; do not invent data. \
                    If evidence suggests a likely root cause, call classify_root_cause (TENTATIVE or PROBABLE).

                    Output: Call submit_stage_finding with a concise evidence summary, \
                    then call terminate(status="success").
                    """),
    ANALYZE(
            "Analyze",
            List.of(StageBuiltin.QUERY_WAREHOUSE),
            McpToolPolicy.NONE,
            30,
            false,
            """
                    Role: ANALYZE stage agent in an incident investigation.

                    Instruction: Reason over the background findings and new tool results. State hypotheses, \
                    supporting evidence, and what remains uncertain.

                    Rules: Prefer query_warehouse only when you need to verify a specific metric or row sample. \
                    Update classify_root_cause when analysis narrows why the incident happened \
                    (replace prior assignment if needed).

                    Output: Call submit_stage_finding with your analysis summary, \
                    then call terminate(status="success").
                    """),
    VALIDATE(
            "Validate",
            List.of(StageBuiltin.QUERY_WAREHOUSE, StageBuiltin.S3, StageBuiltin.KUBERNETES),
            McpToolPolicy.ALL,
            30,
            false,
            """
                    Role: VALIDATE stage agent in an incident investigation.

                    Instruction: Run targeted checks to confirm or reject prior hypotheses.

                    Rules: Prefer decisive validation steps. \
                    Call classify_root_cause with CONFIRMED or revised cause when validation proves or disproves a hypothesis.

                    Output: Call submit_stage_finding with validation results and confidence, \
                    then call terminate(status="success").
                    """),
    TERMINATE(
            "Terminate",
            List.of(),
            McpToolPolicy.NONE,
            3,
            true,
            """
                    Role: TERMINATE stage agent. The investigation is ending.

                    Instruction: Synthesize all prior stage findings into a final incident report: summary, impact, \
                    and recommended actions.

                    Rules: Ensure classify_root_cause reflects the final best-supported root cause (update if needed).

                    Output: When persist_incident_report is available, call it (create or update) with the full markdown \
                    report before submit_stage_finding. Then call submit_stage_finding with a brief conclusion for the \
                    investigation record, then terminate(status="success").
                    """),
    ERROR(
            "Error",
            List.of(),
            McpToolPolicy.NONE,
            3,
            true,
            """
                    Role: ERROR stage agent. The investigation cannot continue normally.

                    Instruction: Document the blocker, what was attempted, and what is needed to resume.

                    Output: Call submit_stage_finding with the error summary, then terminate(status="error").
                    """);

    private final String name;
    private final List<StageBuiltin> builtinTools;
    private final McpToolPolicy mcpToolPolicy;
    private final int maxSteps;
    private final boolean isFinal;
    private final String systemPrompt;

    Stage(
            String name,
            List<StageBuiltin> builtinTools,
            McpToolPolicy mcpToolPolicy,
            int maxSteps,
            boolean isFinal,
            String systemPrompt) {
        this.name = name;
        this.builtinTools = List.copyOf(builtinTools);
        this.mcpToolPolicy = mcpToolPolicy;
        this.maxSteps = maxSteps;
        this.isFinal = isFinal;
        this.systemPrompt = systemPrompt;
    }

    /**
     * Resolves the tool list for this stage: built-ins from the enum plus MCP tools per {@link #mcpToolPolicy}.
     *
     * @param registeredMcpTools MCP catalog from shell (may be empty)
     */
    public List<Object> resolveTools(List<Object> registeredMcpTools) {
        List<Object> tools = new ArrayList<>();
        for (StageBuiltin builtin : builtinTools) {
            tools.add(builtin.createInstance());
        }
        tools.addAll(mcpToolPolicy.select(registeredMcpTools));
        return List.copyOf(tools);
    }

    public String nextStepPrompt() {
        if (this == CLASSIFY) {
            return """
                    Instruction: One action this turn for the Classify stage.

                    Rules:
                    - Classify the **problem** (symptom / user-visible issue), not root cause.
                    - If problem type is not set yet, call classify_incident with one enum value and rationale.
                    - After classify_incident, call submit_stage_finding with the problem-type summary, then terminate.
                    """;
        }
        String rootCauseLine =
                allowsRootCauseClassification()
                        ? "- Update classify_root_cause when evidence supports or changes the root-cause assignment.\n"
                        : "";
        return """
                Instruction: One action this turn for stage %s.

                Rules:
                %s- If evidence or conclusions for this stage are ready, call submit_stage_finding with the full \
                stage output, then call terminate in the next turn.
                - Otherwise use tools to gather or verify information. Do not repeat prior stage summaries verbatim.
                """
                .formatted(name, rootCauseLine);
    }

    /** Root-cause tool is available from COLLECT through final stages (not CLASSIFY / INITIAL). */
    public boolean allowsRootCauseClassification() {
        return this != INITIAL && this != CLASSIFY;
    }

    public static Stage of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        for (Stage stage : Stage.values()) {
            if (stage.name().equalsIgnoreCase(normalized) || stage.name.equalsIgnoreCase(normalized)) {
                return stage;
            }
        }
        return null;
    }

    public static Stage require(String name) {
        Stage stage = of(name);
        if (stage == null) {
            throw new IllegalArgumentException("Unknown stage: " + name);
        }
        return stage;
    }

    public boolean shouldContinue() {
        return !isFinal;
    }
}
