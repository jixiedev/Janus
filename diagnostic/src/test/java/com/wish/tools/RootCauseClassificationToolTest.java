package com.wish.tools;

import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.state.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RootCauseClassificationToolTest {

    @Test
    void classifyRootCauseUpdatesState() {
        State state = new State();
        RootCauseClassificationTool tool = new RootCauseClassificationTool(state);

        tool.classifyRootCause("MISCONFIGURATION", "Wrong spark conf", "TENTATIVE");
        assertEquals(IncidentRootCause.MISCONFIGURATION, state.getRootCause());
        assertEquals(RootCauseConfidence.TENTATIVE, state.getRootCauseConfidence());

        tool.classifyRootCause("INFRASTRUCTURE", "Node NotReady", "CONFIRMED");
        assertEquals(IncidentRootCause.INFRASTRUCTURE, state.getRootCause());
        assertEquals(RootCauseConfidence.CONFIRMED, state.getRootCauseConfidence());
    }
}
