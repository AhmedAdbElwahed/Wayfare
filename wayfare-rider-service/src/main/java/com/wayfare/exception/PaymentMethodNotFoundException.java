package com.wayfare.exception;

import java.util.UUID;

/**
 * Thrown when a payment method id doesn't exist, or exists but belongs to a
 * different rider. Both cases return the same 404 so an attacker can't use
 * this endpoint to enumerate other riders' payment method ids.
 */
public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(UUID id) {
        super("Payment method not found: " + id);
    }
}
