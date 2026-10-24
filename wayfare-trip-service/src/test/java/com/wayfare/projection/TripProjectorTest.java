package com.wayfare.projection;

import com.wayfare.event.DriverMatchedApplied;
import com.wayfare.event.LatLng;
import com.wayfare.event.ProtoTimes;
import com.wayfare.event.RideRequested;
import com.wayfare.event.TripEvent;
import com.wayfare.event.TripStarted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripProjectorTest {

    private final Map<String, TripReadModel> store = new HashMap<>();
    private TripProjector projector;

    private final UUID tripId = UUID.randomUUID();
    private final UUID riderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TripReadRepository repo = mock(TripReadRepository.class);
        when(repo.findById(any(String.class))).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
        when(repo.existsById(any(String.class))).thenAnswer(i -> store.containsKey(i.<String>getArgument(0)));
        when(repo.save(any(TripReadModel.class))).thenAnswer(i -> {
            TripReadModel doc = i.getArgument(0);
            store.put(doc.getId(), doc);
            return doc;
        });
        projector = new TripProjector(repo);
    }

    private TripEvent.Builder base(String eventId) {
        return TripEvent.newBuilder().setEventId(eventId).setTripId(tripId.toString())
                .setOccurredAt(ProtoTimes.toTimestamp(Instant.now()));
    }

    private void requested() {
        projector.project(base("evt-1").setRideRequested(RideRequested.newBuilder()
                .setRiderId(riderId.toString())
                .setPickup(LatLng.newBuilder().setLat(1).setLng(2))
                .setDropoff(LatLng.newBuilder().setLat(3).setLng(4))
                .setFareLocked("12.50")).build());
    }

    @Test
    void rideRequestedCreatesTheDocument() {
        requested();
        TripReadModel doc = store.get(tripId.toString());
        assertThat(doc.getStatus()).isEqualTo("REQUESTED");
        assertThat(doc.getRiderId()).isEqualTo(riderId.toString());
        assertThat(doc.getTimeline()).hasSize(1);
    }

    @Test
    void laterEventsUpdateStatusAndAppendToTheTimeline() {
        requested();
        UUID driver = UUID.randomUUID();
        projector.project(base("evt-2").setDriverMatchedApplied(
                DriverMatchedApplied.newBuilder().setDriverId(driver.toString())).build());

        TripReadModel doc = store.get(tripId.toString());
        assertThat(doc.getStatus()).isEqualTo("MATCHED");
        assertThat(doc.getDriverId()).isEqualTo(driver.toString());
        assertThat(doc.getTimeline()).extracting(TripReadModel.TimelineEntry::status)
                .containsExactly("REQUESTED", "MATCHED");
    }

    @Test
    void redeliveredEventIsAppliedOnlyOnce() {
        requested();
        TripEvent started = base("evt-3").setTripStarted(TripStarted.getDefaultInstance()).build();
        projector.project(started);
        projector.project(started); // same eventId again

        assertThat(store.get(tripId.toString()).getTimeline()).hasSize(2);
    }
}
