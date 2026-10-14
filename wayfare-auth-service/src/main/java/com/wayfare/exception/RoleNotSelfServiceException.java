package com.wayfare.exception;

import com.wayfare.domain.AccountRole;

/**
 * Thrown when public registration asks for a privileged role. Mapped to HTTP
 * 403 by {@link GlobalExceptionHandler}.
 */
public class RoleNotSelfServiceException extends RuntimeException {

    public RoleNotSelfServiceException(AccountRole role) {
        super("Role " + role + " cannot be self-registered");
    }
}
