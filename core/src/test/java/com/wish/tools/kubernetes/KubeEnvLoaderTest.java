package com.wish.tools.kubernetes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeEnvLoaderTest {

    @Test
    void parseEnvFileReadsOverrides(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".kube_env");
        Files.writeString(
                file,
                """
                kubeconfig=/tmp/kubeconfig
                context=prod
                namespace=airflow
                """);

        var map = KubeEnvLoader.parseEnvFile(file);
        assertEquals("/tmp/kubeconfig", map.get("kubeconfig"));
        assertEquals("prod", map.get("context"));
        assertEquals("airflow", map.get("namespace"));
    }

    @Test
    void loadFailsWhenNoKubeconfig(@TempDir Path dir) {
        Path originalDir = Path.of(System.getProperty("user.dir"));
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.dir", dir.toString());
        System.setProperty("user.home", dir.toString());
        try {
            assertThrows(IllegalStateException.class, KubeEnvLoader::load);
        } finally {
            System.setProperty("user.dir", originalDir.toString());
            if (originalHome != null) {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    @Test
    void loadFromKubeEnvFile(@TempDir Path dir) throws Exception {
        Path kubeconfig = dir.resolve("config");
        Files.writeString(kubeconfig, "apiVersion: v1\nkind: Config\n");
        Path file = dir.resolve(".kube_env");
        Files.writeString(
                file,
                """
                kubeconfig=%s
                context=dev
                namespace=default
                """.formatted(kubeconfig));

        Path original = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", dir.toString());
        try {
            KubernetesConnectionConfig config = KubeEnvLoader.load();
            assertEquals(kubeconfig.toAbsolutePath().normalize().toString(), config.kubeconfigPath());
            assertEquals("dev", config.context().orElseThrow());
            assertEquals("default", config.namespace().orElseThrow());
            assertTrue(config.sourceLabel().startsWith("file:"));
        } finally {
            System.setProperty("user.dir", original.toString());
        }
    }
}
