package com.wish.model.incident;

import java.util.Arrays;
import java.util.Locale;

/** Confidence in the current {@link IncidentRootCause} assignment. */
public enum RootCauseConfidence {
    TENTATIVE,
    PROBABLE,
    CONFIRMED;

    public static RootCauseConfidence require(String name) {
        RootCauseConfidence value = of(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Unknown confidence: " + name + ". Allowed: TENTATIVE, PROBABLE, CONFIRMED");
        }
        return value;
    }

    public static RootCauseConfidence of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(v -> v.name().equals(normalized)).findFirst().orElse(null);
    }
}
