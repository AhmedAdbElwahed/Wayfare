package com.wayfare.domain;

public enum DriverStatus {
    /** Account exists; the driver is still filling in profile, vehicle and documents. */
    ONBOARDING,
    /** Submitted for review; waiting on an admin decision. */
    PENDING_APPROVAL,
    APPROVED,
    SUSPENDED
}
