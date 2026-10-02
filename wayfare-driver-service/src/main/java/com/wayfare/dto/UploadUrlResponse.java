package com.wayfare.dto;

import java.time.Instant;

import com.wayfare.storage.PresignedUpload;

/** HTTP PUT the file to {@code uploadUrl} with the same Content-Type, then register {@code objectKey}. */
public record UploadUrlResponse(String uploadUrl, String objectKey, Instant expiresAt) {
    public static UploadUrlResponse from(PresignedUpload u) {
        return new UploadUrlResponse(u.uploadUrl(), u.objectKey(), u.expiresAt());
    }
}
