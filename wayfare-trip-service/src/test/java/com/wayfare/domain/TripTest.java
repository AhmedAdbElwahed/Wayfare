package com.wayfare.domain;

import com.wayfare.event.TripDomainEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private final UUID rider = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();

    private Trip pending() {
        return Trip.create(rider, new GeoPoint(30.0, 31.0), new GeoPoint(30.1, 31.1), new BigDecimal("10.00"));
    }

    @Test
    void happyPathWalksTheWholeLifecycleAndEmitsOneEventPerStep() {
        Trip trip = pending();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PENDING);

        TripDomainEvent.RideRequested requested = trip.confirm();
        assertThat(requested.riderId()).isEqualTo(rider);
        assertThat(requested.fareLocked()).isEqualByComparingTo("10.00");

        trip.applyDriverMatched(driver);
        trip.driverArriving();
        trip.start();
        TripDomainEvent.TripCompleted done = trip.complete(4.2, new BigDecimal("10.00"));

        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(done.driverId()).isEqualTo(driver);
        assertThat(trip.getEndedAt()).isNotNull();
    }

    @Test
    void illegalTransitionsAreRejected() {
        Trip trip = pending();
        assertThatThrownBy(trip::start).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> trip.applyDriverMatched(driver))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void cancelledTripsCannotBeCancelledAgain() {
        Trip trip = pending();
        trip.cancel(rider.toString(), "changed my mind");
        assertThatThrownBy(() -> trip.cancel(rider.toString(), "again"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void tripInProgressCannotBeCancelled() {
        Trip trip = pending();
        trip.confirm();
        trip.applyDriverMatched(driver);
        trip.driverArriving();
        trip.start();
        assertThatThrownBy(() -> trip.cancel("x", null)).isInstanceOf(IllegalStateTransitionException.class);
    }
}
