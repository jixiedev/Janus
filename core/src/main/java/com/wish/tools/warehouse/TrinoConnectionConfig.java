package com.wish.tools.warehouse;

import java.util.Properties;

/** Resolved Trino JDBC settings from {@code .presto_env} or environment variables. */
public record TrinoConnectionConfig(String jdbcUrl, String username, String password, String sourceLabel) {

    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        if (password != null && !password.isBlank()) {
            properties.setProperty("password", password);
        }
        return properties;
    }
}
