package com.wayfare.dto;

import com.wayfare.domain.DocumentStatus;

import jakarta.validation.constraints.NotNull;

/** Admin decision on an uploaded document; only VALID and REJECTED are accepted. */
public record ReviewDocumentRequest(@NotNull DocumentStatus status) {
}
