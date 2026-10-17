package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;

/** Re-authentication for an irreversible action: the caller must prove they know the credentials. */
public record DeleteAccountRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
