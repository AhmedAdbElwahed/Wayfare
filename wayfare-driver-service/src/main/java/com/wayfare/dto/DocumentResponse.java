package com.wayfare.dto;

import java.time.Instant;
import java.util.UUID;

import com.wayfare.domain.DocumentKind;
import com.wayfare.domain.DocumentStatus;
import com.wayfare.domain.DriverDocument;

/**
 * {@code downloadUrl} is a short-lived presigned link; the bucket itself is
 * private.
 */
public record DocumentResponse(UUID id, DocumentKind kind, String downloadUrl, DocumentStatus status,
        Instant expiresAt) {
    public static DocumentResponse from(DriverDocument d, String downloadUrl) {
        return new DocumentResponse(d.getId(), d.getKind(), downloadUrl, d.getStatus(), d.getExpiresAt());
    }
}
