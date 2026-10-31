package com.wayfare.service;

/**
 * Holds a driver for one trip so no other trip can be matched to them.
 * First writer wins; a reservation expires on its own after the configured TTL.
 */
public interface DriverReservation {

    /** True if this call reserved {@code driverId} for {@code tripId}. */
    boolean tryReserve(String driverId, String tripId);

    /** Frees {@code driverId}, but only if it is still reserved for {@code tripId}. */
    void release(String driverId, String tripId);

    /** Frees whichever driver is reserved for {@code tripId}, if any. */
    void releaseTrip(String tripId);
}
