package com.wish.tools.s3;

import java.util.Optional;

/** Resolved S3 client settings from {@code .s3_env} or environment variables. */
public record S3ConnectionConfig(
        String accessKeyId,
        String secretAccessKey,
        String region,
        Optional<String> endpointUrl,
        boolean pathStyleAccess,
        String sourceLabel) {}
