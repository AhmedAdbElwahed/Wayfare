package com.wayfare.event;

import com.wayfare.domain.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Facts that happened to a trip, named in the past tense. Every state change in
 * {@link com.wayfare.domain.Trip} returns one of these; the command service then
 * stores it in the event log and in the outbox.
 *
 * <p>Sealed so the set of events is closed and visible in one file.
 * {@link #eventType()} is the string written to {@code trip_events.type} and to
 * the Kafka {@code eventType} header, which the projector switches on.
 */
public sealed interface TripDomainEvent {

    UUID tripId();

    Instant occurredAt();

    String eventType();

    record RideRequested(UUID tripId, UUID riderId, GeoPoint pickup, GeoPoint dropoff,
                         BigDecimal fareLocked, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "RideRequested"; }
    }

    record DriverMatchedApplied(UUID tripId, UUID driverId, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "DriverMatchedApplied"; }
    }

    record NoDriversFound(UUID tripId, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "NoDriversFound"; }
    }

    record DriverArriving(UUID tripId, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "DriverArriving"; }
    }

    record TripStarted(UUID tripId, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "TripStarted"; }
    }

    record TripCompleted(UUID tripId, UUID riderId, UUID driverId, BigDecimal finalFare,
                         double distanceKm, Instant occurredAt) implements TripDomainEvent {
        public String eventType() { return "TripCompleted"; }
    }

    record TripCancelled(UUID tripId, String cancelledBy, String reason, Instant occurredAt)
            implements TripDomainEvent {
        public String eventType() { return "TripCancelled"; }
    }
}
