package com.wayfare.exception;

import java.util.UUID;

/**
 * Thrown when no {@code profiles} row exists for the given account id.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(UUID id) {
        super("Profile not found: " + id);
    }
}
