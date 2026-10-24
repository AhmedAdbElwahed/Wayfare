package com.wayfare.domain;

import com.wayfare.event.TripDomainEvent;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The write-side aggregate. It owns the lifecycle rules: each command method
 * checks the transition is legal, mutates the state, and <em>returns the domain
 * event describing what happened</em>. It never touches a repository or Kafka —
 * that is {@link com.wayfare.service.TripCommandService}'s job — which keeps the
 * rules testable with plain {@code new}.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs a no-arg constructor
public class Trip {

    @Id
    private UUID id;
    private UUID riderId;
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    // Two GeoPoints in one table: rename the columns of each so they don't clash.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "lat", column = @Column(name = "pickup_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "pickup_lng"))})
    private GeoPoint pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "lat", column = @Column(name = "dropoff_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "dropoff_lng"))})
    private GeoPoint dropoff;

    private BigDecimal fareLocked;
    private BigDecimal fareFinal;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant endedAt;

    /**
     * Optimistic lock: Hibernate adds {@code WHERE version = ?} to every UPDATE and
     * bumps it. Two concurrent commands on the same trip -> the second UPDATE
     * matches 0 rows -> OptimisticLockingFailureException instead of a lost update.
     */
    @Version
    private Long version;

    /** Starts a trip in PENDING: priced, but not yet visible to matching. */
    public static Trip create(UUID riderId, GeoPoint pickup, GeoPoint dropoff, BigDecimal fareLocked) {
        Trip trip = new Trip();
        trip.id = UUID.randomUUID();
        trip.riderId = riderId;
        trip.pickup = pickup;
        trip.dropoff = dropoff;
        trip.fareLocked = fareLocked;
        trip.status = TripStatus.PENDING;
        return trip;
    }

    public TripDomainEvent.RideRequested confirm() {
        moveTo(TripStatus.REQUESTED);
        requestedAt = Instant.now();
        return new TripDomainEvent.RideRequested(id, riderId, pickup, dropoff, fareLocked, requestedAt);
    }

    public TripDomainEvent.DriverMatchedApplied applyDriverMatched(UUID driverId) {
        moveTo(TripStatus.MATCHED);
        this.driverId = driverId;
        return new TripDomainEvent.DriverMatchedApplied(id, driverId, Instant.now());
    }

    public TripDomainEvent.NoDriversFound applyNoDriversFound() {
        moveTo(TripStatus.NO_DRIVERS);
        return new TripDomainEvent.NoDriversFound(id, Instant.now());
    }

    public TripDomainEvent.DriverArriving driverArriving() {
        moveTo(TripStatus.ARRIVING);
        return new TripDomainEvent.DriverArriving(id, Instant.now());
    }

    public TripDomainEvent.TripStarted start() {
        moveTo(TripStatus.IN_PROGRESS);
        startedAt = Instant.now();
        return new TripDomainEvent.TripStarted(id, startedAt);
    }

    public TripDomainEvent.TripCompleted complete(double distanceKm, BigDecimal finalFare) {
        moveTo(TripStatus.COMPLETED);
        endedAt = Instant.now();
        fareFinal = finalFare;
        return new TripDomainEvent.TripCompleted(id, riderId, driverId, finalFare, distanceKm, endedAt);
    }

    public TripDomainEvent.TripCancelled cancel(String cancelledBy, String reason) {
        moveTo(TripStatus.CANCELLED);
        endedAt = Instant.now();
        return new TripDomainEvent.TripCancelled(id, cancelledBy, reason, endedAt);
    }

    private void moveTo(TripStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateTransitionException(status, next);
        }
        status = next;
    }
}
