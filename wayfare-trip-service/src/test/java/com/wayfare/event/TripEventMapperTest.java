package com.wayfare.event;

import com.wayfare.domain.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripEventMapperTest {

    private final TripEventMapper mapper = new TripEventMapper();
    private final UUID tripId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Test
    void envelopeCarriesIdsAndTimestamp() {
        Instant now = Instant.parse("2026-01-01T10:00:00.123Z");
        TripEvent wire = mapper.toProto(eventId, new TripDomainEvent.TripStarted(tripId, now));

        assertThat(wire.getEventId()).isEqualTo(eventId.toString());
        assertThat(wire.getTripId()).isEqualTo(tripId.toString());
        assertThat(ProtoTimes.toInstant(wire.getOccurredAt())).isEqualTo(now);
        assertThat(wire.getEventCase()).isEqualTo(TripEvent.EventCase.TRIP_STARTED);
    }

    @Test
    void rideRequestedKeepsCoordinatesAndExactFare() {
        var event = new TripDomainEvent.RideRequested(tripId, UUID.randomUUID(), new GeoPoint(30.5, 31.5),
                new GeoPoint(30.6, 31.6), new BigDecimal("12.50"), Instant.now());
        TripEvent wire = mapper.toProto(eventId, event);

        assertThat(wire.getRideRequested().getPickup().getLat()).isEqualTo(30.5);
        assertThat(wire.getRideRequested().getDropoff().getLng()).isEqualTo(31.6);
        assertThat(wire.getRideRequested().getFareLocked()).isEqualTo("12.50");
    }

    @Test
    void cancellationReasonIsOptional() {
        var without = mapper.toProto(eventId, new TripDomainEvent.TripCancelled(tripId, "rider", null, Instant.now()));
        var with = mapper.toProto(eventId, new TripDomainEvent.TripCancelled(tripId, "rider", "late", Instant.now()));

        assertThat(without.getTripCancelled().hasReason()).isFalse();
        assertThat(with.getTripCancelled().getReason()).isEqualTo("late");
    }
}
