package com.wayfare.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wayfare.domain.DocumentKind;
import com.wayfare.domain.DriverDocument;
import com.wayfare.storage.ObjectStorage;
import com.wayfare.storage.PresignedUpload;

import lombok.RequiredArgsConstructor;

/**
 * Driver-specific rules on top of {@link ObjectStorage}. Documents go to the
 * private bucket under {@code drivers/{id}/documents/}, profile photos to the
 * public media bucket under {@code drivers/{id}/photo/}. The per-driver prefix
 * is what {@code requireUploaded} enforces, so a driver can only register
 * objects that were issued for them.
 */
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final ObjectStorage storage;

    private static String documentPrefix(UUID driverId) {
        return "drivers/" + driverId + "/documents/";
    }

    private static String photoPrefix(UUID driverId) {
        return "drivers/" + driverId + "/photo/";
    }

    public PresignedUpload documentUploadUrl(UUID driverId, DocumentKind kind, String contentType) {
        String key = ObjectStorage.newKey(documentPrefix(driverId) + kind.name().toLowerCase() + "-", contentType, DOCUMENT_TYPES);
        return storage.presignUpload(storage.documentsBucket(), key, UPLOAD_TTL);
    }

    public void verifyDocument(UUID driverId, String objectKey) {
        storage.requireUploaded(storage.documentsBucket(), objectKey, documentPrefix(driverId),
                MAX_DOCUMENT_BYTES, DOCUMENT_TYPES);
    }

    public String downloadUrl(DriverDocument doc) {
        return storage.presignDownload(storage.documentsBucket(), doc.getObjectKey(), DOWNLOAD_TTL);
    }

    public PresignedUpload photoUploadUrl(UUID driverId, String contentType) {
        String key = ObjectStorage.newKey(photoPrefix(driverId), contentType, PHOTO_TYPES);
        return storage.presignUpload(storage.mediaBucket(), key, UPLOAD_TTL);
    }

    /** Validates the uploaded photo and returns its public URL. */
    public String verifyPhoto(UUID driverId, String objectKey) {
        storage.requireUploaded(storage.mediaBucket(), objectKey, photoPrefix(driverId), MAX_PHOTO_BYTES, PHOTO_TYPES);
        return storage.publicUrl(objectKey);
    }
}
