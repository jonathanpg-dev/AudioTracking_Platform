package com.AudioTracking.Platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

// Wires the AWS S3 SDK to talk to Cloudflare R2 instead of real AWS S3 — R2's S3-compatible API
// means only the endpoint/credentials/region differ, nothing about how the SDK itself is used.
// All values come from environment variables (see application.properties); no credential is ever
// hard-coded here or committed anywhere in the repo.
@Configuration
public class StorageConfig {

    private final String endpoint;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;

    public StorageConfig(@Value("${r2.endpoint}") String endpoint,
                          @Value("${r2.access-key-id}") String accessKeyId,
                          @Value("${r2.secret-access-key}") String secretAccessKey,
                          @Value("${r2.region:auto}") String region) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region = region;
    }

    @Bean
    public S3Client r2Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentialsProvider())
                .region(Region.of(region))
                // R2 (like most S3-compatible providers) expects path-style addressing
                // (endpoint/bucket/key) rather than AWS's default virtual-hosted-style
                // (bucket.endpoint/key).
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner r2Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentialsProvider())
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }
}
