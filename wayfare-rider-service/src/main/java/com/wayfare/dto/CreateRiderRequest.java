package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRiderRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String photoUrl,
        String locale) {
}
