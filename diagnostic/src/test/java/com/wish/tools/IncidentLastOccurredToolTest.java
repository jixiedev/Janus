package com.wish.tools;

import com.wish.model.state.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentLastOccurredToolTest {

    @Test
    void recordIncidentLastOccurredUpdatesState() {
        State state = new State();
        IncidentLastOccurredTool tool = new IncidentLastOccurredTool(state);

        tool.recordIncidentLastOccurred("2026-06-09T02:00:00Z");

        assertEquals(Instant.parse("2026-06-09T02:00:00Z"), state.getIncidentOccurredAt());
    }
}
