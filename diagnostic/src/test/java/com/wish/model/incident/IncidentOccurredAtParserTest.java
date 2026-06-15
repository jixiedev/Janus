package com.wish.model.incident;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentOccurredAtParserTest {

    @Test
    void parsesInstantOffsetDateAndDate() {
        assertEquals(Instant.parse("2026-06-08T03:00:00Z"), IncidentOccurredAtParser.parseRequired("2026-06-08T03:00:00Z"));
        assertEquals(
                Instant.parse("2026-06-08T03:00:00Z"),
                IncidentOccurredAtParser.parseRequired("2026-06-08T03:00:00+00:00"));
        assertEquals(Instant.parse("2026-06-08T00:00:00Z"), IncidentOccurredAtParser.parseRequired("2026-06-08"));
    }

    @Test
    void rejectsBlankOccurredAt() {
        assertThrows(IllegalArgumentException.class, () -> IncidentOccurredAtParser.parseRequired(" "));
    }
}
