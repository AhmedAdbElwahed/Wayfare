package com.wayfare.dto;

import com.wayfare.domain.PaymentMethod;

import java.util.UUID;

// providerToken is intentionally excluded — never echo the tokenized
// provider reference back to a client, only display-safe metadata.
public record PaymentMethodResponse(UUID id, String brand, String last4, boolean isDefault) {
    public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodResponse(
                paymentMethod.getId(),
                paymentMethod.getBrand(),
                paymentMethod.getLast4(),
                paymentMethod.isDefault());
    }
}
