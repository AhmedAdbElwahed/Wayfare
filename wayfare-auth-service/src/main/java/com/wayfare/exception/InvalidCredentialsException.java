package com.wayfare.exception;

/**
 * Thrown when login fails for any reason (unknown email, wrong password, or a
 * non-active account). The message is deliberately generic so the response
 * never reveals which of those was the case. Mapped to HTTP 401 by
 * {@link GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
