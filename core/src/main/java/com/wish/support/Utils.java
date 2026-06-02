package com.wish.support;

import java.util.Locale;

public final class Utils {

    private Utils() {}

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String trimToDefault(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    public static String normalizeModelAlias(String model) {
        return model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
    }
}
