package com.wish.model.state;

import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StateTest {

    @Test
    void descIncludesIncidentCategory() {
        State state = new State();
        state.setIncidentClassification(IncidentCategory.DATA_ISSUE, "Orders table null rate above threshold");
        state.addFinding(Stage.CLASSIFY, "Problem type: incorrect/missing data in orders.");

        String description = state.desc();
        assertTrue(description.contains("DATA_ISSUE"));
        assertTrue(description.contains("Problem type"));
        assertTrue(description.contains("null rate above threshold"));
        assertTrue(description.contains("CLASSIFY"));
    }

    @Test
    void descIncludesRootCause() {
        State state = new State();
        state.setRootCauseClassification(
                IncidentRootCause.DEPLOYMENT_OR_RELEASE,
                "Deploy at 02:00 preceded first failure",
                RootCauseConfidence.CONFIRMED);

        String description = state.desc();
        assertTrue(description.contains("DEPLOYMENT_OR_RELEASE"));
        assertTrue(description.contains("CONFIRMED"));
        assertTrue(description.contains("Deploy at 02:00"));
    }
}
