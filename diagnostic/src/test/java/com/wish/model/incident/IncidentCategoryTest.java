package com.wish.model.incident;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentCategoryTest {

    @Test
    void ofAcceptsEnumNameAndLabel() {
        assertEquals(IncidentCategory.JOB_EXECUTION, IncidentCategory.of("JOB_EXECUTION"));
        assertEquals(IncidentCategory.ORCHESTRATION, IncidentCategory.of("Orchestration"));
    }

    @Test
    void ofMapsLegacyCauseOrientedNamesToProblemTypes() {
        assertEquals(IncidentCategory.DATA_ISSUE, IncidentCategory.of("DATA_QUALITY"));
        assertEquals(IncidentCategory.JOB_EXECUTION, IncidentCategory.of("PIPELINE_FAILURE"));
        assertEquals(IncidentCategory.PERFORMANCE, IncidentCategory.of("CAPACITY"));
    }

    @Test
    void requireRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> IncidentCategory.require("NOT_A_CATEGORY"));
    }
}
