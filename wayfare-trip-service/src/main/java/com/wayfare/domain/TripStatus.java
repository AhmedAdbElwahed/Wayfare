package com.wayfare.domain;

import java.util.Set;

/**
 * The trip lifecycle (see the state diagram in docs-spring-boot/09-trip-service.md).
 * The legal transitions live here, next to the states, so {@link Trip} can ask
 * "may I move to X?" without hard-coding if-chains in every command method.
 */
public enum TripStatus {
    /** Created with a fare estimate; the rider has not confirmed yet. */
    PENDING,
    /** Confirmed by the rider; waiting for the matching service to find a driver. */
    REQUESTED,
    MATCHED,
    ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVERS;

    public Set<TripStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(REQUESTED, CANCELLED);
            case REQUESTED -> Set.of(MATCHED, NO_DRIVERS, CANCELLED);
            case MATCHED -> Set.of(ARRIVING, CANCELLED);
            case ARRIVING -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED);
            case COMPLETED, CANCELLED, NO_DRIVERS -> Set.of();
        };
    }

    public boolean canMoveTo(TripStatus next) {
        return allowedNext().contains(next);
    }
}
