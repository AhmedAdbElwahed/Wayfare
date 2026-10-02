package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;

public record PhotoRequest(@NotBlank String objectKey) {
}
