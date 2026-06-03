package com.wish.tools.s3;

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
 * Loads S3 credentials from {@code .s3_env} then environment variables.
 *
 * <p>File / env keys (case-insensitive): {@code access_key_id}, {@code secret_access_key}, {@code region};
 * optional {@code endpoint_url}, {@code path_style_access=true} (for MinIO and compatible endpoints).
 *
 * <p>Environment fallback: {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}, {@code AWS_REGION},
 * {@code AWS_ENDPOINT_URL}, {@code AWS_S3_PATH_STYLE_ACCESS}.
 */
public final class S3EnvLoader {

    public static final String S3_ENV_FILE_NAME = ".s3_env";
    public static final String S3_ENV_FILE_PROPERTY = "s3.env.file";

    private S3EnvLoader() {}

    public static S3ConnectionConfig load() {
        Map<String, String> fromFile = loadFileMap().orElse(Map.of());
        Map<String, String> merged = new LinkedHashMap<>(fromFile);
        loadEnvironmentMap().forEach(merged::putIfAbsent);

        String sourceLabel;
        if (isComplete(fromFile)) {
            sourceLabel = "file:" + resolveConfigFilePath().map(Path::toString).orElse(S3_ENV_FILE_NAME);
        } else if (isComplete(merged) && !fromFile.isEmpty()) {
            sourceLabel = "file+env:" + resolveConfigFilePath().map(Path::toString).orElse(S3_ENV_FILE_NAME);
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
        String accessKey = first(values, "access_key_id", "aws_access_key_id");
        String secret = first(values, "secret_access_key", "aws_secret_access_key");
        String region = first(values, "region", "aws_region", "aws_default_region");
        return accessKey != null
                && !accessKey.isBlank()
                && secret != null
                && !secret.isBlank()
                && region != null
                && !region.isBlank();
    }

    private static S3ConnectionConfig buildConfig(Map<String, String> values, String sourceLabel) {
        String accessKey = first(values, "access_key_id", "aws_access_key_id").trim();
        String secret = first(values, "secret_access_key", "aws_secret_access_key");
        String region = first(values, "region", "aws_region", "aws_default_region").trim();
        String endpoint = optionalFirst(values, "endpoint_url", "endpoint", "aws_endpoint_url").orElse(null);
        boolean pathStyle = parseBoolean(first(values, "path_style_access", "aws_s3_path_style_access"));
        if (endpoint != null && !endpoint.isBlank() && !pathStyle) {
            pathStyle = true;
        }
        return new S3ConnectionConfig(
                accessKey,
                secret,
                region,
                endpoint == null || endpoint.isBlank() ? Optional.empty() : Optional.of(endpoint.trim()),
                pathStyle,
                sourceLabel);
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
        putEnv(map, "access_key_id", "AWS_ACCESS_KEY_ID");
        putEnv(map, "secret_access_key", "AWS_SECRET_ACCESS_KEY");
        putEnv(map, "region", "AWS_REGION", "AWS_DEFAULT_REGION");
        putEnv(map, "endpoint_url", "AWS_ENDPOINT_URL");
        putEnv(map, "path_style_access", "AWS_S3_PATH_STYLE_ACCESS");
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
        String explicit = System.getProperty(S3_ENV_FILE_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("S3_ENV_FILE");
        }
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(Path.of(explicit.trim()));
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).resolve(S3_ENV_FILE_NAME);
        if (Files.isRegularFile(cwd)) {
            return Optional.of(cwd);
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path homeFile = Path.of(home).resolve(S3_ENV_FILE_NAME);
            if (Files.isRegularFile(homeFile)) {
                return Optional.of(homeFile);
            }
        }
        return Optional.empty();
    }

    private static String buildConfigErrorMessage(boolean fileMissing) {
        String fileHint = fileMissing
                ? "Create .s3_env in the working directory or $HOME, or set S3_ENV_FILE."
                : ".s3_env was found but is missing required fields.";
        return """
                S3 credentials are not configured. %s
                Required: access_key_id, secret_access_key, region.
                .s3_env example:
                  access_key_id=AKIA...
                  secret_access_key=...
                  region=us-east-1
                  endpoint_url=https://minio.example.com
                  path_style_access=true
                Environment fallback: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION; \
                optional AWS_ENDPOINT_URL, AWS_S3_PATH_STYLE_ACCESS.
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
