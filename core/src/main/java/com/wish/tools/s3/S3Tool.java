package com.wish.tools.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Read-only S3 operations for agents. Credentials: {@link S3EnvLoader} ({@code .s3_env} first, then AWS env vars).
 */
@Slf4j
public class S3Tool {

    public static final String LIST_TOOL = "s3_list_objects";
    public static final String GET_TOOL = "s3_get_object";
    public static final String HEAD_TOOL = "s3_head_object";

    private static final int DEFAULT_MAX_KEYS = 100;
    private static final int MAX_ALLOWED_KEYS = 1000;
    private static final int DEFAULT_MAX_BYTES = 65_536;
    private static final int MAX_ALLOWED_BYTES = 262_144;

    @Tool(
            name = LIST_TOOL,
            description =
                    "List objects in an S3 bucket under an optional prefix. Returns keys, sizes, and last modified times.")
    public String listObjects(
            @ToolParam(description = "S3 bucket name", required = true) String bucket,
            @ToolParam(description = "Key prefix filter (optional)", required = false) String prefix,
            @ToolParam(description = "Maximum keys to return (default 100, max 1000)", required = false)
                    Integer maxKeys) {
        int limit = normalizeMaxKeys(maxKeys);
        try (S3Client client = openClient()) {
            ListObjectsV2Request.Builder request =
                    ListObjectsV2Request.builder().bucket(bucket).maxKeys(limit);
            if (prefix != null && !prefix.isBlank()) {
                request.prefix(prefix.trim());
            }
            ListObjectsV2Response response = client.listObjectsV2(request.build());
            return formatResult(formatListResponse(bucket, prefix, response, limit), true);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (S3Exception e) {
            log.warn("s3_list_objects failed: {}", e.getMessage());
            return formatResult("S3 list failed: " + e.getMessage(), false);
        }
    }

    @Tool(
            name = GET_TOOL,
            description =
                    "Download an S3 object body as text (UTF-8). Binary objects may be truncated or garbled; use head first for metadata.")
    public String getObject(
            @ToolParam(description = "S3 bucket name", required = true) String bucket,
            @ToolParam(description = "Object key", required = true) String key,
            @ToolParam(description = "Maximum bytes to read (default 65536, max 262144)", required = false)
                    Integer maxBytes) {
        int limit = normalizeMaxBytes(maxBytes);
        try (S3Client client = openClient()) {
            try (ResponseInputStream<GetObjectResponse> stream =
                    client.getObject(GetObjectRequest.builder().bucket(bucket).key(key.trim()).build())) {
                byte[] data = stream.readNBytes(limit + 1);
                boolean truncated = data.length > limit;
                if (truncated) {
                    data = java.util.Arrays.copyOf(data, limit);
                }
                String body = new String(data, StandardCharsets.UTF_8);
                return formatResult(
                        "bucket=%s%nkey=%s%nsize_bytes=%d%ntruncated=%s%ncontent:%n%s"
                                .formatted(bucket, key, data.length, truncated, body),
                        true);
            }
        } catch (NoSuchKeyException e) {
            return formatResult("Object not found: s3://%s/%s".formatted(bucket, key), false);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (Exception e) {
            log.warn("s3_get_object failed: {}", e.getMessage());
            return formatResult("S3 get failed: " + e.getMessage(), false);
        }
    }

    @Tool(
            name = HEAD_TOOL,
            description = "Read S3 object metadata (size, etag, content type, last modified) without downloading the body.")
    public String headObject(
            @ToolParam(description = "S3 bucket name", required = true) String bucket,
            @ToolParam(description = "Object key", required = true) String key) {
        try (S3Client client = openClient()) {
            HeadObjectResponse response =
                    client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key.trim()).build());
            return formatResult(
                    """
                            bucket=%s
                            key=%s
                            content_length=%d
                            content_type=%s
                            etag=%s
                            last_modified=%s
                            """
                            .formatted(
                                    bucket,
                                    key,
                                    response.contentLength(),
                                    nullToEmpty(response.contentType()),
                                    nullToEmpty(response.eTag()),
                                    response.lastModified()),
                    true);
        } catch (NoSuchKeyException e) {
            return formatResult("Object not found: s3://%s/%s".formatted(bucket, key), false);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (S3Exception e) {
            log.warn("s3_head_object failed: {}", e.getMessage());
            return formatResult("S3 head failed: " + e.getMessage(), false);
        }
    }

    private static S3Client openClient() {
        S3ConnectionConfig config = S3EnvLoader.load();
        log.debug("S3 client using {}", config.sourceLabel());
        return S3Clients.create(config);
    }

    private static String formatListResponse(
            String bucket, String prefix, ListObjectsV2Response response, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("bucket=").append(bucket).append('\n');
        if (prefix != null && !prefix.isBlank()) {
            sb.append("prefix=").append(prefix.trim()).append('\n');
        }
        sb.append("truncated=").append(Boolean.TRUE.equals(response.isTruncated())).append('\n');
        List<S3Object> objects = response.contents() == null ? List.of() : response.contents();
        int count = 0;
        for (S3Object object : objects) {
            if (count >= limit) {
                break;
            }
            count++;
            sb.append("object ")
                    .append(count)
                    .append(": key=")
                    .append(object.key())
                    .append(" size=")
                    .append(object.size())
                    .append(" modified=")
                    .append(formatInstant(object.lastModified()))
                    .append('\n');
        }
        sb.append("count=").append(count);
        return sb.toString().trim();
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int normalizeMaxKeys(Integer maxKeys) {
        if (maxKeys == null || maxKeys <= 0) {
            return DEFAULT_MAX_KEYS;
        }
        return Math.min(maxKeys, MAX_ALLOWED_KEYS);
    }

    private static int normalizeMaxBytes(Integer maxBytes) {
        if (maxBytes == null || maxBytes <= 0) {
            return DEFAULT_MAX_BYTES;
        }
        return Math.min(maxBytes, MAX_ALLOWED_BYTES);
    }

    private static String formatResult(String observation, boolean success) {
        return "{observation=%s, success=%s}".formatted(observation, success);
    }
}
