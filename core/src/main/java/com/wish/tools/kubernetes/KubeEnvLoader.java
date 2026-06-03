package com.wish.tools.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves kubeconfig path and optional context/namespace from {@code .kube_env} then environment.
 *
 * <p>File / env keys (case-insensitive): {@code kubeconfig}; optional {@code context}, {@code namespace}.
 *
 * <p>Environment: {@code KUBECONFIG}; optional {@code KUBE_CONTEXT}, {@code KUBERNETES_CONTEXT},
 * {@code KUBE_NAMESPACE}, {@code KUBERNETES_NAMESPACE}.
 */
public final class KubeEnvLoader {

    public static final String KUBE_ENV_FILE_NAME = ".kube_env";
    public static final String KUBE_ENV_FILE_PROPERTY = "kube.env.file";

    private KubeEnvLoader() {}

    public static KubernetesConnectionConfig load() {
        Map<String, String> fromFile = loadFileMap().orElse(Map.of());
        Map<String, String> merged = new LinkedHashMap<>(fromFile);
        loadEnvironmentMap().forEach(merged::putIfAbsent);

        String kubeconfigPath = resolveKubeconfigPath(merged)
                .orElseThrow(() -> new IllegalStateException(buildConfigErrorMessage(fromFile.isEmpty())));

        Path path = Path.of(kubeconfigPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Kubeconfig file not found: " + kubeconfigPath);
        }

        String sourceLabel;
        if (fromFile.containsKey("kubeconfig") || fromFile.containsKey("config")) {
            sourceLabel = "file:" + resolveConfigFilePath().map(Path::toString).orElse(KUBE_ENV_FILE_NAME);
        } else if (!fromFile.isEmpty()) {
            sourceLabel = "file+env:" + resolveConfigFilePath().map(Path::toString).orElse(KUBE_ENV_FILE_NAME);
        } else if (System.getenv("KUBECONFIG") != null && !System.getenv("KUBECONFIG").isBlank()) {
            sourceLabel = "environment:KUBECONFIG";
        } else {
            sourceLabel = "default:~/.kube/config";
        }

        Optional<String> context = optionalFirst(merged, "context", "kube_context", "kubernetes_context");
        Optional<String> namespace = optionalFirst(merged, "namespace", "kube_namespace", "kubernetes_namespace");

        return new KubernetesConnectionConfig(
                path.toAbsolutePath().normalize().toString(), context, namespace, sourceLabel);
    }

    private static Optional<String> resolveKubeconfigPath(Map<String, String> merged) {
        String explicit = first(merged, "kubeconfig", "config", "kube_config");
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(explicit.trim());
        }
        String kubeconfigEnv = System.getenv("KUBECONFIG");
        if (kubeconfigEnv != null && !kubeconfigEnv.isBlank()) {
            String firstPath = kubeconfigEnv.split(java.io.File.pathSeparator)[0].trim();
            if (!firstPath.isEmpty()) {
                return Optional.of(firstPath);
            }
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path defaultConfig = Path.of(home).resolve(".kube").resolve("config");
            if (Files.isRegularFile(defaultConfig)) {
                return Optional.of(defaultConfig.toString());
            }
        }
        return Optional.empty();
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
        putEnv(map, "kubeconfig", "KUBECONFIG");
        putEnv(map, "context", "KUBE_CONTEXT", "KUBERNETES_CONTEXT");
        putEnv(map, "namespace", "KUBE_NAMESPACE", "KUBERNETES_NAMESPACE");
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
        String explicit = System.getProperty(KUBE_ENV_FILE_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("KUBE_ENV_FILE");
        }
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(Path.of(explicit.trim()));
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).resolve(KUBE_ENV_FILE_NAME);
        if (Files.isRegularFile(cwd)) {
            return Optional.of(cwd);
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path homeFile = Path.of(home).resolve(KUBE_ENV_FILE_NAME);
            if (Files.isRegularFile(homeFile)) {
                return Optional.of(homeFile);
            }
        }
        return Optional.empty();
    }

    private static String buildConfigErrorMessage(boolean fileMissing) {
        String fileHint = fileMissing
                ? "Create .kube_env in the working directory or $HOME, or set KUBE_ENV_FILE."
                : ".kube_env was found but kubeconfig path could not be resolved.";
        return """
                Kubernetes is not configured. %s
                Provide a readable kubeconfig file via:
                  - .kube_env: kubeconfig=/path/to/config (optional context=..., namespace=...)
                  - KUBECONFIG environment variable
                  - ~/.kube/config (default)
                .kube_env example:
                  kubeconfig=/Users/me/.kube/config
                  context=prod-cluster
                  namespace=data-platform
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
        return value == null ? Optional.empty() : Optional.of(value.trim());
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
