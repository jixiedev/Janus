package com.wish.tools.warehouse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads Trino connection settings from {@code .presto_env} then environment variables.
 *
 * <p>Supported {@code .presto_env} entries (case-insensitive keys, {@code export KEY=value} allowed):
 * {@code jdbc_url} / {@code url}, or {@code host} + {@code port} + optional {@code catalog} / {@code schema};
 * {@code user} / {@code username}; {@code password}; optional {@code ssl=true}.
 *
 * <p>Environment fallback (used when the file is missing or incomplete):
 * {@code PRESTO_JDBC_URL} or {@code TRINO_JDBC_URL}, or {@code PRESTO_HOST}/{@code TRINO_HOST} with
 * {@code PRESTO_PORT}, {@code PRESTO_USER}, {@code PRESTO_PASSWORD}, optional {@code PRESTO_CATALOG},
 * {@code PRESTO_SCHEMA}, {@code PRESTO_SSL} (Trino-prefixed names accepted too).
 */
public final class PrestoEnvLoader {

    public static final String PRESTO_ENV_FILE_NAME = ".presto_env";
    public static final String PRESTO_ENV_FILE_PROPERTY = "presto.env.file";

    private PrestoEnvLoader() {}

    public static TrinoConnectionConfig load() {
        Map<String, String> fromFile = loadFileMap().orElse(Map.of());
        Map<String, String> merged = new LinkedHashMap<>(fromFile);
        loadEnvironmentMap().forEach(merged::putIfAbsent);

        String sourceLabel;
        if (isComplete(fromFile)) {
            sourceLabel = "file:" + resolveConfigFilePath().map(Path::toString).orElse(PRESTO_ENV_FILE_NAME);
        } else if (isComplete(merged) && !fromFile.isEmpty()) {
            sourceLabel = "file+env:" + resolveConfigFilePath().map(Path::toString).orElse(PRESTO_ENV_FILE_NAME);
        } else if (isComplete(loadEnvironmentMap())) {
            sourceLabel = "environment";
        } else {
            throw new IllegalStateException(buildConfigErrorMessage(fromFile.isEmpty()));
        }

        return buildConfig(merged, sourceLabel);
    }

    private static boolean isComplete(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String user = first(values, "user", "username", "presto_user", "trino_user");
        String password = first(values, "password", "presto_password", "trino_password");
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        String jdbcUrl = resolveJdbcUrl(values);
        return jdbcUrl != null && !jdbcUrl.isBlank();
    }

    private static TrinoConnectionConfig buildConfig(Map<String, String> values, String sourceLabel) {
        String user = first(values, "user", "username", "presto_user", "trino_user").trim();
        String password = first(values, "password", "presto_password", "trino_password");
        String jdbcUrl = resolveJdbcUrl(values);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("Trino JDBC URL could not be built from configuration");
        }
        return new TrinoConnectionConfig(jdbcUrl, user, password, sourceLabel);
    }

    static String resolveJdbcUrl(Map<String, String> values) {
        String explicit =
                first(values, "jdbc_url", "jdbc.url", "url", "presto_jdbc_url", "trino_jdbc_url", "presto_url", "trino_url");
        if (explicit != null && !explicit.isBlank()) {
            return normalizeJdbcUrl(explicit.trim());
        }
        String host = first(values, "host", "presto_host", "trino_host");
        if (host == null || host.isBlank()) {
            return null;
        }
        String port = optionalFirst(values, "port", "presto_port", "trino_port").orElse("8080");
        String catalog = optionalFirst(values, "catalog", "presto_catalog", "trino_catalog").orElse("");
        String schema = optionalFirst(values, "schema", "presto_schema", "trino_schema").orElse("");
        boolean ssl = parseBoolean(first(values, "ssl", "presto_ssl", "trino_ssl"));

        StringBuilder url = new StringBuilder("jdbc:trino://").append(host.trim()).append(":").append(port.trim());
        if (!catalog.isBlank()) {
            url.append('/').append(catalog.trim());
            if (!schema.isBlank()) {
                url.append('/').append(schema.trim());
            }
        }
        if (ssl) {
            url.append(url.indexOf("?") >= 0 ? "&SSL=true" : "?SSL=true");
        }
        return url.toString();
    }

    private static String normalizeJdbcUrl(String url) {
        if (url.startsWith("jdbc:presto://")) {
            return "jdbc:trino://" + url.substring("jdbc:presto://".length());
        }
        return url;
    }

    private static Optional<Map<String, String>> loadFileMap() {
        Optional<Path> path = resolveConfigFilePath();
        if (path.isEmpty() || !Files.isRegularFile(path.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseEnvFile(path.get()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path.get() + ": " + e.getMessage(), e);
        }
    }

    static Map<String, String> parseEnvFile(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = unquote(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Map<String, String> loadEnvironmentMap() {
        Map<String, String> map = new LinkedHashMap<>();
        putEnv(map, "jdbc_url", "PRESTO_JDBC_URL", "TRINO_JDBC_URL", "JDBC_URL");
        putEnv(map, "host", "PRESTO_HOST", "TRINO_HOST");
        putEnv(map, "port", "PRESTO_PORT", "TRINO_PORT");
        putEnv(map, "catalog", "PRESTO_CATALOG", "TRINO_CATALOG");
        putEnv(map, "schema", "PRESTO_SCHEMA", "TRINO_SCHEMA");
        putEnv(map, "user", "PRESTO_USER", "TRINO_USER");
        putEnv(map, "password", "PRESTO_PASSWORD", "TRINO_PASSWORD");
        putEnv(map, "ssl", "PRESTO_SSL", "TRINO_SSL");
        return map;
    }

    private static void putEnv(Map<String, String> map, String key, String... envNames) {
        for (String envName : envNames) {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                map.put(key, value.trim());
                return;
            }
        }
    }

    static Optional<Path> resolveConfigFilePath() {
        String explicit = System.getProperty(PRESTO_ENV_FILE_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("PRESTO_ENV_FILE");
        }
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(Path.of(explicit.trim()));
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).resolve(PRESTO_ENV_FILE_NAME);
        if (Files.isRegularFile(cwd)) {
            return Optional.of(cwd);
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path homeFile = Path.of(home).resolve(PRESTO_ENV_FILE_NAME);
            if (Files.isRegularFile(homeFile)) {
                return Optional.of(homeFile);
            }
        }
        return Optional.empty();
    }

    private static String buildConfigErrorMessage(boolean fileMissing) {
        String fileHint = fileMissing
                ? "Create .presto_env in the working directory or $HOME, or set PRESTO_ENV_FILE."
                : ".presto_env was found but is missing required fields.";
        return """
                Trino warehouse credentials are not configured. %s
                Required: user/username and password, plus jdbc_url (or host).
                .presto_env example:
                  host=trino.example.com
                  port=443
                  catalog=hive
                  schema=default
                  user=myuser
                  password=secret
                  ssl=true
                Environment fallback: PRESTO_HOST, PRESTO_USER, PRESTO_PASSWORD (or TRINO_* equivalents), \
                optionally PRESTO_JDBC_URL.
                """
                .formatted(fileHint);
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key.toLowerCase(Locale.ROOT));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Optional<String> optionalFirst(Map<String, String> map, String... keys) {
        String value = first(map, keys);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private static boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
