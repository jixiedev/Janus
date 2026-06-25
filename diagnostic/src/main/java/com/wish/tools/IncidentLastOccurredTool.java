package com.wish.tools;

import com.wish.model.incident.IncidentOccurredAtParser;
import com.wish.model.state.State;

import java.time.Instant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.wish.prompt.DiagnosticPrompts.RECORD_INCIDENT_LAST_OCCURRED_TOOL;

/**
 * Refines {@link State#getIncidentOccurredAt()} from evidence (e.g. latest failed DAG run).
 * Available from COLLECT onward; CLASSIFY sets a provisional estimate via {@link IncidentClassificationTool}.
 */
public class IncidentLastOccurredTool {

    private final State state;

    public IncidentLastOccurredTool(State state) {
        this.state = state;
    }

    @Tool(
            name = RECORD_INCIDENT_LAST_OCCURRED_TOOL,
            description =
                    "Record when the incident last occurred, from tool evidence (e.g. the most recent failed "
                            + "DAG run within the user's scope). Call when a precise latest-failure timestamp is known.")
    public String recordIncidentLastOccurred(
            @ToolParam(
                            description =
                                    "ISO-8601 UTC instant of the most recent failure or observation within the user "
                                            + "scope — not the earliest historical failure.",
                            required = true)
                    String occurredAt) {
        Instant occurred = IncidentOccurredAtParser.parseRequired(occurredAt);
        state.setIncidentOccurredAt(occurred);
        return "{lastOccurredAt=%s}".formatted(occurred);
    }
}
