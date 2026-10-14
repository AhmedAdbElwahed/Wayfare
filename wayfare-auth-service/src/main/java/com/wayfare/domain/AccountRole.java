package com.wayfare.domain;

public enum AccountRole {
    RIDER, DRIVER,
    /** Never self-registered: provisioned at start-up by {@code AdminBootstrap}. */
    ADMIN;

    /** Roles a caller may pick for themselves through {@code POST /auth/register}. */
    public boolean isSelfService() {
        return this != ADMIN;
    }
}
