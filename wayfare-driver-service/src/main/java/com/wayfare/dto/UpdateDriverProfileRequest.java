package com.wayfare.dto;

import jakarta.validation.constraints.Size;

/** Partial update: null fields are left as they are. */
public record UpdateDriverProfileRequest(
        @Size(min = 1, max = 255) String name,
        @Size(min = 5, max = 50) String phone) {
}
