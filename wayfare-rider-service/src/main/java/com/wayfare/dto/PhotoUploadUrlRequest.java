package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;

public record PhotoUploadUrlRequest(@NotBlank String contentType) {
}
