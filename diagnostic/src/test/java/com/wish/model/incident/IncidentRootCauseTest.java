package com.wish.model.incident;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentRootCauseTest {

    @Test
    void ofAcceptsEnumName() {
        assertEquals(IncidentRootCause.MISCONFIGURATION, IncidentRootCause.of("MISCONFIGURATION"));
        assertEquals(IncidentRootCause.INFRASTRUCTURE, IncidentRootCause.of("Infrastructure"));
    }

    @Test
    void requireRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> IncidentRootCause.require("BAD_CAUSE"));
    }
}
