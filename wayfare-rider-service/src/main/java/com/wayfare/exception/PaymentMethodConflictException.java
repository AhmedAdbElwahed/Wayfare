package com.wayfare.exception;

import java.util.UUID;

/**
 * Thrown when a concurrent request already changed this rider's default
 * payment method, tripping the {@code uq_payment_methods_one_default}
 * database constraint. Mapped to HTTP 409 by {@link GlobalExceptionHandler};
 * the caller should retry.
 */
public class PaymentMethodConflictException extends RuntimeException {

    public PaymentMethodConflictException(UUID userId) {
        super("Default payment method for rider " + userId + " was changed by another request — retry");
    }
}
