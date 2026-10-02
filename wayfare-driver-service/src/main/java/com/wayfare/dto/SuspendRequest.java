package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;

public record SuspendRequest(@NotBlank String reason) {
}
