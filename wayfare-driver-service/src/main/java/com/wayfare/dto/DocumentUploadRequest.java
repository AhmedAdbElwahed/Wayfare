package com.wayfare.dto;

import java.time.Instant;

import com.wayfare.domain.DocumentKind;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Step 2 of a document upload: register a file the client has already PUT to MinIO. */
public record DocumentUploadRequest(
        @NotNull DocumentKind kind,
        @NotBlank @Size(max = 500) String objectKey,
        @NotNull @Future Instant expiresAt) {
}
