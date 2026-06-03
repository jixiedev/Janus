package com.wish.tools.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3EnvLoaderTest {

    @Test
    void parseEnvFileReadsCredentials(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".s3_env");
        Files.writeString(
                file,
                """
                access_key_id=AKIATEST
                secret_access_key='secret123'
                region=us-west-2
                endpoint_url=https://s3.local
                path_style_access=true
                """);

        var map = S3EnvLoader.parseEnvFile(file);
        assertEquals("AKIATEST", map.get("access_key_id"));
        assertEquals("secret123", map.get("secret_access_key"));
        assertEquals("us-west-2", map.get("region"));
    }

    @Test
    void loadFailsWhenNoConfig(@TempDir Path dir) {
        Path original = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", dir.toString());
        try {
            assertThrows(IllegalStateException.class, S3EnvLoader::load);
        } finally {
            System.setProperty("user.dir", original.toString());
        }
    }

    @Test
    void loadFromEnvFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".s3_env");
        Files.writeString(
                file,
                """
                access_key_id=key
                secret_access_key=sec
                region=eu-central-1
                """);
        Path original = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", dir.toString());
        try {
            S3ConnectionConfig config = S3EnvLoader.load();
            assertEquals("key", config.accessKeyId());
            assertEquals("sec", config.secretAccessKey());
            assertEquals("eu-central-1", config.region());
            assertTrue(config.sourceLabel().startsWith("file:"));
        } finally {
            System.setProperty("user.dir", original.toString());
        }
    }
}
