package com.wish.tools.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrestoEnvLoaderTest {

    @Test
    void parseEnvFileSupportsExportAndQuotes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".presto_env");
        Files.writeString(
                file,
                """
                # comment
                export host='trino.example.com'
                port=443
                user=alice
                password="s3cret"
                catalog=hive
                schema=default
                ssl=true
                """);

        Map<String, String> map = PrestoEnvLoader.parseEnvFile(file);
        assertEquals("trino.example.com", map.get("host"));
        assertEquals("alice", map.get("user"));
        assertEquals("s3cret", map.get("password"));

        String url = PrestoEnvLoader.resolveJdbcUrl(map);
        assertTrue(url.startsWith("jdbc:trino://trino.example.com:443/hive/default"));
        assertTrue(url.contains("SSL=true"));
    }

    @Test
    void resolveJdbcUrlFromExplicitUrl() {
        String url = PrestoEnvLoader.resolveJdbcUrl(Map.of(
                "jdbc_url", "jdbc:presto://coordinator:8080/hive/default",
                "user", "u",
                "password", "p"));
        assertEquals("jdbc:trino://coordinator:8080/hive/default", url);
    }

    @Test
    void loadFailsWhenNoConfig(@TempDir Path dir) {
        Path original = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", dir.toString());
        try {
            assertThrows(IllegalStateException.class, PrestoEnvLoader::load);
        } finally {
            System.setProperty("user.dir", original.toString());
        }
    }
}
