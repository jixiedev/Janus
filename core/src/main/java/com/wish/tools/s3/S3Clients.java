package com.wish.tools.s3;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

final class S3Clients {

    private S3Clients() {}

    static S3Client create(S3ConnectionConfig config) {
        S3ClientBuilder builder =
                S3Client.builder()
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey())))
                        .region(Region.of(config.region()));

        config.endpointUrl()
                .filter(url -> !url.isBlank())
                .ifPresent(
                        endpoint -> {
                            builder.endpointOverride(URI.create(endpoint));
                            if (config.pathStyleAccess()) {
                                builder.serviceConfiguration(
                                        S3Configuration.builder().pathStyleAccessEnabled(true).build());
                            }
                        });

        return builder.build();
    }
}
