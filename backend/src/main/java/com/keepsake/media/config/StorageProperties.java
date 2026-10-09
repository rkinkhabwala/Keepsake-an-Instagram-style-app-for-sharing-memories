package com.keepsake.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint       S3 endpoint the backend talks to (e.g. http://minio:9000 inside Compose)
 * @param publicEndpoint S3 endpoint browsers use for presigned URLs (e.g. http://localhost:9000)
 */
@ConfigurationProperties(prefix = "keepsake.s3")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {

    public String resolvedPublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
