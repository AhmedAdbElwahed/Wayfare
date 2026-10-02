package com.wayfare.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code endpoint} is how this service reaches MinIO (inside Compose:
 * {@code http://minio:9000}). {@code publicEndpoint} is how a browser or phone
 * reaches it, and is what presigned URLs are built against — a signature is
 * bound to the host it was made for, so a URL signed for {@code minio:9000}
 * is useless to a client outside the Docker network.
 */
@ConfigurationProperties("wayfare.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String region,
        String documentsBucket,
        String mediaBucket) {

    public StorageProperties {
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            publicEndpoint = endpoint;
        }
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (documentsBucket == null || documentsBucket.isBlank()) {
            documentsBucket = "wayfare-documents";
        }
        if (mediaBucket == null || mediaBucket.isBlank()) {
            mediaBucket = "wayfare-media";
        }
    }
}
