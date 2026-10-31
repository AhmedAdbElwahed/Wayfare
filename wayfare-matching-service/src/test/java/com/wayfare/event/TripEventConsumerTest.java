package com.wayfare.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import com.wayfare.service.DriverReservation;
import com.wayfare.service.MatchingService;

class TripEventConsumerTest {

    MatchingService matching = mock(MatchingService.class);
    DriverReservation reservation = mock(DriverReservation.class);
    TripEventConsumer consumer = new TripEventConsumer(matching, reservation);

    @Test
    void rideRequestedDispatchesFromThePickup() {
        consumer.onTripEvent().accept(TripEvent.newBuilder().setTripId("t1")
                .setRideRequested(RideRequested.newBuilder()
                        .setPickup(LatLng.newBuilder().setLat(30.1).setLng(31.2)))
                .build());

        verify(matching).dispatch("t1", 30.1, 31.2);
    }

    @Test
    void completedAndCancelledReleaseTheDriver() {
        consumer.onTripEvent().accept(TripEvent.newBuilder().setTripId("t1")
                .setTripCompleted(TripCompleted.getDefaultInstance()).build());
        consumer.onTripEvent().accept(TripEvent.newBuilder().setTripId("t2")
                .setTripCancelled(TripCancelled.getDefaultInstance()).build());

        verify(reservation).releaseTrip("t1");
        verify(reservation).releaseTrip("t2");
        verifyNoInteractions(matching);
    }

    @Test
    void otherEventsAreIgnored() {
        consumer.onTripEvent().accept(TripEvent.newBuilder().setTripId("t1")
                .setTripStarted(TripStarted.getDefaultInstance()).build());
        consumer.onTripEvent().accept(TripEvent.newBuilder().setTripId("t1").build());

        verifyNoInteractions(matching, reservation);
    }
}
