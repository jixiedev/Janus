package com.wish.model.incident;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Parses agent-supplied incident occurrence timestamps (ISO-8601). */
public final class IncidentOccurredAtParser {

    private IncidentOccurredAtParser() {}

    public static Instant parseRequired(String text) {
        Instant parsed = parseOptional(text);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "occurredAt is required (ISO-8601 instant or offset datetime, e.g. 2026-06-08T03:00:00Z)");
        }
        return parsed;
    }

    public static Instant parseOptional(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid occurredAt '"
                            + text
                            + "'. Use ISO-8601 instant or date, e.g. 2026-06-08T03:00:00Z or 2026-06-08",
                    e);
        }
    }
}
