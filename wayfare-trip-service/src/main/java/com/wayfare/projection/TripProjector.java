package com.wayfare.projection;

import com.wayfare.domain.GeoPoint;
import com.wayfare.event.ProtoTimes;
import com.wayfare.event.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Builds the MongoDB read model by replaying the trip events from Kafka.
 *
 * <p>The bean name {@code onTripEvent} is the binding name
 * ({@code onTripEvent-in-0} in the config-repo yml). The Confluent deserializer
 * delivers a ready {@link TripEvent}; each handler loads the document, skips the
 * event if its {@code eventId} was already applied, changes the document and
 * saves it. Throwing makes Spring Cloud Stream retry, then dead-letter.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TripProjector {

    private final TripReadRepository readRepo;

    @Bean
    public Consumer<TripEvent> onTripEvent() {
        return this::project;
    }

    /** Public for tests: the same entry point the binder uses, minus Kafka. */
    public void project(TripEvent e) {
        Instant at = ProtoTimes.toInstant(e.getOccurredAt());
        switch (e.getEventCase()) {
            case RIDE_REQUESTED -> onRideRequested(e, at);
            case DRIVER_MATCHED_APPLIED -> update(e, "MATCHED", at,
                    doc -> doc.setDriverId(e.getDriverMatchedApplied().getDriverId()));
            case NO_DRIVERS_FOUND -> update(e, "NO_DRIVERS", at, doc -> { });
            case DRIVER_ARRIVING -> update(e, "ARRIVING", at, doc -> { });
            case TRIP_STARTED -> update(e, "IN_PROGRESS", at, doc -> { });
            case TRIP_COMPLETED -> update(e, "COMPLETED", at, doc -> {
                doc.setFareFinal(new BigDecimal(e.getTripCompleted().getFinalFare()));
                doc.setDistanceKm(e.getTripCompleted().getDistanceKm());
            });
            case TRIP_CANCELLED -> update(e, "CANCELLED", at, doc -> { });
            // A newer producer may add event types this consumer doesn't know yet.
            case EVENT_NOT_SET -> log.warn("Ignoring trip event {} with no known payload", e.getEventId());
        }
    }

    /** RideRequested is the first event of a trip, so it creates the document. */
    private void onRideRequested(TripEvent e, Instant at) {
        if (readRepo.existsById(e.getTripId())) {
            return; // redelivery
        }
        var rr = e.getRideRequested();
        TripReadModel doc = new TripReadModel();
        doc.setId(e.getTripId());
        doc.setRiderId(rr.getRiderId());
        doc.setPickup(new GeoPoint(rr.getPickup().getLat(), rr.getPickup().getLng()));
        doc.setDropoff(new GeoPoint(rr.getDropoff().getLat(), rr.getDropoff().getLng()));
        doc.setFareLocked(new BigDecimal(rr.getFareLocked()));
        doc.setStatus("REQUESTED");
        doc.setCreatedAt(at);
        doc.getTimeline().add(new TripReadModel.TimelineEntry("REQUESTED", at));
        doc.getProcessedEventIds().add(e.getEventId());
        readRepo.save(doc);
    }

    private void update(TripEvent e, String status, Instant at, Consumer<TripReadModel> change) {
        TripReadModel doc = readRepo.findById(e.getTripId()).orElseThrow(
                () -> new IllegalStateException("Read model for trip " + e.getTripId() + " is missing"));
        if (!doc.getProcessedEventIds().add(e.getEventId())) {
            return; // already applied
        }
        change.accept(doc);
        doc.setStatus(status);
        doc.getTimeline().add(new TripReadModel.TimelineEntry(status, at));
        readRepo.save(doc);
    }
}
