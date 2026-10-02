package com.wayfare.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

/**
 * Thin wrapper over the MinIO client. Files never flow through the services:
 * they hand out a short-lived presigned PUT URL, the client uploads straight to
 * the store, then the service checks the object with {@link #stat} before it
 * records the key.
 *
 * <p>Two clients on purpose: {@code internal} does the real calls
 * (stat/delete/bucket setup) over the address reachable from this service,
 * {@code signer} only builds URLs against the public address. Because a region
 * is set explicitly, signing is a local computation with no network call.
 */
public class ObjectStorage {

    private final MinioClient internal;
    private final MinioClient signer;
    private final StorageProperties props;

    public ObjectStorage(StorageProperties props) {
        this.props = props;
        this.internal = MinioClient.builder()
                .endpoint(props.endpoint()).credentials(props.accessKey(), props.secretKey())
                .region(props.region()).build();
        this.signer = MinioClient.builder()
                .endpoint(props.publicEndpoint()).credentials(props.accessKey(), props.secretKey())
                .region(props.region()).build();
    }

    public String documentsBucket() {
        return props.documentsBucket();
    }

    public String mediaBucket() {
        return props.mediaBucket();
    }

    /** Creates the buckets if missing. The media bucket is made world-readable, documents stay private. */
    public void ensureBuckets() throws Exception {
        ensureBucket(props.documentsBucket());
        ensureBucket(props.mediaBucket());
        internal.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(props.mediaBucket()).config("""
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}"""
                .formatted(props.mediaBucket())).build());
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!internal.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            internal.makeBucket(MakeBucketArgs.builder().bucket(bucket).region(props.region()).build());
        }
    }

    public PresignedUpload presignUpload(String bucket, String key, Duration ttl) {
        return new PresignedUpload(presign(Method.PUT, bucket, key, ttl), key, Instant.now().plus(ttl));
    }

    public String presignDownload(String bucket, String key, Duration ttl) {
        return presign(Method.GET, bucket, key, ttl);
    }

    /** Direct URL for objects in the public media bucket. */
    public String publicUrl(String key) {
        return props.publicEndpoint().replaceAll("/+$", "") + "/" + props.mediaBucket() + "/" + key;
    }

    /** Empty if the object doesn't exist (the upload never happened). */
    public Optional<StoredObject> stat(String bucket, String key) {
        try {
            StatObjectResponse r = internal.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new StoredObject(key, r.size(), r.contentType()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchObject".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new StorageException("stat failed for " + key, e);
        } catch (Exception e) {
            throw new StorageException("stat failed for " + key, e);
        }
    }

    public void delete(String bucket, String key) {
        try {
            internal.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("delete failed for " + key, e);
        }
    }

    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", "pdf", "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    /**
     * A fresh, unguessable key under {@code prefix} (which should end in "/").
     * Rejects content types outside {@code allowedTypes} up front, before a URL
     * is issued.
     */
    public static String newKey(String prefix, String contentType, Set<String> allowedTypes) {
        if (contentType == null || !allowedTypes.contains(contentType) || !EXTENSIONS.containsKey(contentType)) {
            throw new InvalidUploadException("Unsupported content type; allowed: " + allowedTypes);
        }
        return prefix + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    }

    /**
     * Confirms the client really uploaded what it claims before the key is
     * recorded. The key must sit under {@code requiredPrefix} — that's what
     * stops a user registering someone else's object — and the stored size and
     * content type are checked against the limits, because a presigned PUT
     * can't enforce them itself. An object that fails the checks is deleted.
     */
    public StoredObject requireUploaded(String bucket, String key, String requiredPrefix,
            long maxBytes, Set<String> allowedTypes) {
        if (key == null || !key.startsWith(requiredPrefix) || key.contains("..")) {
            throw new InvalidUploadException("Object key is not valid for this resource");
        }
        StoredObject object = stat(bucket, key)
                .orElseThrow(() -> new InvalidUploadException("No uploaded file found for that key"));
        if (object.size() > maxBytes || object.size() == 0 || !allowedTypes.contains(object.contentType())) {
            delete(bucket, key);
            throw new InvalidUploadException(
                    "Uploaded file must be non-empty, at most " + maxBytes + " bytes, of type " + allowedTypes);
        }
        return object;
    }

    private String presign(Method method, String bucket, String key, Duration ttl) {
        try {
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method).bucket(bucket).object(key)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception e) {
            throw new StorageException("presign failed for " + key, e);
        }
    }
}
