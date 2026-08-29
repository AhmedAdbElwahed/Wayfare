package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddPaymentMethodRequest(
        @NotBlank String providerToken,
        String brand,
        @Pattern(regexp = "\\d{4}") String last4,
        boolean isDefault
) {
}
