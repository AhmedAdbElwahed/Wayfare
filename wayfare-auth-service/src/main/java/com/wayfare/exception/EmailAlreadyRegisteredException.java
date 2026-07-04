package com.wayfare.exception;

/**
 * Thrown when registration is attempted with an email that already has an
 * account. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email already registered: " + email);
    }
}
