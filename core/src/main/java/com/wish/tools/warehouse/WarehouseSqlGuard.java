package com.wish.tools.warehouse;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Allows read-only Trino SQL for {@link QueryWarehouseTool}. */
final class WarehouseSqlGuard {

    private static final Pattern LEADING_COMMENTS =
            Pattern.compile("^(?:\\s|--[^\\n]*\\n|/\\*[\\s\\S]*?\\*/)*", Pattern.MULTILINE);

    private static final Set<String> FORBIDDEN_FIRST_TOKEN = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "CREATE", "DROP", "ALTER", "TRUNCATE", "GRANT", "REVOKE",
            "CALL", "SET", "RESET", "PREPARE", "DEALLOCATE", "EXECUTE");

    private WarehouseSqlGuard() {}

    static String validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        String normalized = stripLeadingComments(sql.trim());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        String firstToken = firstToken(normalized);
        if (firstToken == null) {
            throw new IllegalArgumentException("Could not parse SQL");
        }
        String upper = firstToken.toUpperCase(Locale.ROOT);
        if (FORBIDDEN_FIRST_TOKEN.contains(upper)) {
            throw new IllegalArgumentException("Only read-only queries are allowed (SELECT/WITH/SHOW/DESCRIBE/EXPLAIN)");
        }
        if (!Set.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN").contains(upper)) {
            throw new IllegalArgumentException(
                    "Only read-only queries are allowed; got statement starting with " + firstToken);
        }
        if (normalized.contains(";")) {
            String withoutTrailing = normalized.replaceAll(";\\s*$", "");
            if (withoutTrailing.contains(";")) {
                throw new IllegalArgumentException("Multiple SQL statements are not allowed");
            }
        }
        return normalized;
    }

    private static String stripLeadingComments(String sql) {
        return LEADING_COMMENTS.matcher(sql).replaceAll("").trim();
    }

    private static String firstToken(String sql) {
        int i = 0;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        if (i >= sql.length()) {
            return null;
        }
        int start = i;
        while (i < sql.length() && !Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return sql.substring(start, i);
    }
}
