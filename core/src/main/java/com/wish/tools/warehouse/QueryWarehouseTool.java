package com.wish.tools.warehouse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only Trino SQL for diagnostic / data investigation.
 * Credentials: {@link PrestoEnvLoader} ({@code .presto_env} first, then environment variables).
 */
@Slf4j
public class QueryWarehouseTool {

    public static final String TOOL_NAME = "query_warehouse";

    private static final int DEFAULT_MAX_ROWS = 100;
    private static final int MAX_ALLOWED_ROWS = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 120;
    private static final int MAX_CELL_CHARS = 500;
    private static final int MAX_OUTPUT_CHARS = 32_000;

    static {
        try {
            Class.forName("io.trino.jdbc.TrinoDriver");
        } catch (ClassNotFoundException e) {
            log.warn("Trino JDBC driver not on classpath; query_warehouse will fail until trino-jdbc is present");
        }
    }

    @Tool(
            name = TOOL_NAME,
            description =
                    "Run a read-only Trino SQL query against the data warehouse (SELECT/WITH/SHOW/DESCRIBE/EXPLAIN only). "
                            + "Returns a truncated row set. Configure connection via .presto_env or PRESTO_* / TRINO_* env vars.")
    public String queryWarehouse(
            @ToolParam(description = "Read-only Trino SQL", required = true) String sql,
            @ToolParam(description = "Maximum rows to return (default 100, max 500)", required = false)
                    Integer maxRows) {
        return queryWarehouse(sql, maxRows == null ? DEFAULT_MAX_ROWS : maxRows);
    }

    public String queryWarehouse(String sql, int maxRows) {
        int rowLimit = normalizeMaxRows(maxRows);
        String validatedSql;
        try {
            validatedSql = WarehouseSqlGuard.validateReadOnly(sql);
        } catch (IllegalArgumentException e) {
            return formatResult(e.getMessage(), false);
        }

        TrinoConnectionConfig config;
        try {
            config = PrestoEnvLoader.load();
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        }

        log.info("query_warehouse using {} as user {}", config.sourceLabel(), config.username());
        try {
            String result = executeQuery(config, validatedSql, rowLimit);
            return formatResult(result, true);
        } catch (SQLException e) {
            log.warn("query_warehouse failed: {}", e.getMessage());
            return formatResult("Trino query failed: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("query_warehouse failed: {}", e.getMessage());
            return formatResult(e.getMessage(), false);
        }
    }

    private static int normalizeMaxRows(Integer maxRows) {
        if (maxRows == null || maxRows <= 0) {
            return DEFAULT_MAX_ROWS;
        }
        return Math.min(maxRows, MAX_ALLOWED_ROWS);
    }

    private static String executeQuery(TrinoConnectionConfig config, String sql, int maxRows) throws SQLException {
        try (Connection connection =
                DriverManager.getConnection(config.jdbcUrl(), config.connectionProperties())) {
            try (Statement statement = connection.createStatement()) {
                statement.setMaxRows(maxRows);
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = statement.executeQuery(sql)) {
                    return formatResultSet(rs, maxRows, config.sourceLabel());
                }
            }
        }
    }

    private static String formatResultSet(ResultSet rs, int maxRows, String sourceLabel) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> headers = new ArrayList<>(columnCount);
        for (int c = 1; c <= columnCount; c++) {
            String label = meta.getColumnLabel(c);
            headers.add(label == null || label.isBlank() ? "col" + c : label);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("source=").append(sourceLabel).append('\n');
        sb.append("columns=").append(String.join(", ", headers)).append('\n');

        int rowCount = 0;
        boolean truncated = false;
        while (rs.next()) {
            rowCount++;
            if (rowCount > maxRows) {
                truncated = true;
                break;
            }
            sb.append("row ").append(rowCount).append(": ");
            for (int c = 1; c <= columnCount; c++) {
                if (c > 1) {
                    sb.append(" | ");
                }
                sb.append(headers.get(c - 1)).append('=');
                sb.append(truncateCell(rs.getString(c)));
            }
            sb.append('\n');
            if (sb.length() > MAX_OUTPUT_CHARS) {
                truncated = true;
                sb.append("...(output truncated)\n");
                break;
            }
        }

        sb.append("row_count=").append(Math.min(rowCount, maxRows));
        if (truncated) {
            sb.append(" (truncated to max_rows=").append(maxRows).append(')');
        }
        return sb.toString().trim();
    }

    private static String truncateCell(String value) {
        if (value == null) {
            return "NULL";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= MAX_CELL_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CELL_CHARS) + "...";
    }

    private static String formatResult(String observation, boolean success) {
        return "{observation=%s, success=%s}".formatted(observation, success);
    }
}
