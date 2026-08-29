package com.wayfare.exception;

import java.util.UUID;

/**
 * Thrown when profile creation is attempted for an account id that already
 * has a {@code profiles} row. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ProfileAlreadyExistsException extends RuntimeException {

    public ProfileAlreadyExistsException(UUID id) {
        super("Profile already exists: " + id);
    }
}
