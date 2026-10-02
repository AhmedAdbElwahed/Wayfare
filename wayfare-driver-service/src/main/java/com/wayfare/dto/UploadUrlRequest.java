package com.wayfare.dto;

import com.wayfare.domain.DocumentKind;

import jakarta.validation.constraints.NotBlank;

/** {@code kind} is required for documents and ignored for photos. */
public record UploadUrlRequest(DocumentKind kind, @NotBlank String contentType) {
}
