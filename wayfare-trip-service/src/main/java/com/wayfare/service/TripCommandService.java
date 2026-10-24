package com.wayfare.service;

import com.wayfare.domain.GeoPoint;
import com.wayfare.domain.OutboxEvent;
import com.wayfare.domain.Trip;
import com.wayfare.domain.TripEventRecord;
import com.wayfare.dto.TripDto;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.wayfare.event.TripDomainEvent;
import com.wayfare.event.TripEvent;
import com.wayfare.event.TripEventMapper;
import com.wayfare.exception.TripAccessDeniedException;
import com.wayfare.exception.TripNotFoundException;
import com.wayfare.repository.OutboxRepository;
import com.wayfare.repository.TripEventRepository;
import com.wayfare.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The command side of CQRS: every method is one transaction that
 * <ol>
 *   <li>changes the {@link Trip} (current state),</li>
 *   <li>appends the resulting event to {@code trip_events} (history),</li>
 *   <li>inserts the same event into {@code outbox} (to be sent to Kafka).</li>
 * </ol>
 * All three commit together or not at all. Nothing here talks to Kafka — that is
 * the whole point of the outbox: the broker can be down and commands still work.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripCommandService {

    private final TripRepository tripRepo;
    private final TripEventRepository eventRepo;
    private final OutboxRepository outboxRepo;
    private final FareEstimator fareEstimator;
    private final TripEventMapper eventMapper;

    /** Creates a PENDING trip with a locked fare. No event yet: nothing has happened that others care about. */
    public TripDto createTrip(UUID riderId, GeoPoint pickup, GeoPoint dropoff) {
        BigDecimal fare = fareEstimator.estimate(pickup, dropoff);
        Trip trip = tripRepo.save(Trip.create(riderId, pickup, dropoff, fare));
        return TripDto.from(trip);
    }

    public void confirmTrip(UUID tripId, UUID riderId) {
        Trip trip = loadTrip(tripId);
        requireRider(trip, riderId);
        record(trip, trip.confirm());
    }

    public void cancelTrip(UUID tripId, UUID callerId, String reason) {
        Trip trip = loadTrip(tripId);
        requireParticipant(trip, callerId);
        record(trip, trip.cancel(callerId.toString(), reason));
    }

    // --- commands driven by the matching service's events -------------------

    public void applyDriverMatched(UUID tripId, UUID driverId) {
        Trip trip = loadTrip(tripId);
        record(trip, trip.applyDriverMatched(driverId));
    }

    public void applyNoDriversFound(UUID tripId) {
        Trip trip = loadTrip(tripId);
        record(trip, trip.applyNoDriversFound());
    }

    // --- commands issued by the assigned driver -----------------------------

    public void driverArriving(UUID tripId, UUID driverId) {
        Trip trip = loadTrip(tripId);
        requireDriver(trip, driverId);
        record(trip, trip.driverArriving());
    }

    public void startTrip(UUID tripId, UUID driverId) {
        Trip trip = loadTrip(tripId);
        requireDriver(trip, driverId);
        record(trip, trip.start());
    }

    public void completeTrip(UUID tripId, UUID driverId, Double distanceKm) {
        Trip trip = loadTrip(tripId);
        requireDriver(trip, driverId);
        double km = distanceKm != null ? distanceKm
                : fareEstimator.distanceKm(trip.getPickup(), trip.getDropoff());
        // Flat pricing for now: the final fare is the fare locked at creation.
        record(trip, trip.complete(km, trip.getFareLocked()));
    }

    /**
     * The two inserts that make the pattern work. The same protobuf event is
     * stored twice, in two forms:
     * <ul>
     *   <li>{@code trip_events}: readable JSON (permanent audit history),</li>
     *   <li>{@code outbox}: binary protobuf (the wire format; a to-do item for the relay).</li>
     * </ul>
     * The outbox row id doubles as the event id so consumers can dedup redeliveries.
     */
    private void record(Trip trip, TripDomainEvent event) {
        tripRepo.save(trip);
        UUID eventId = UUID.randomUUID();
        TripEvent proto = eventMapper.toProto(eventId, event);
        eventRepo.save(new TripEventRecord(trip.getId(), event.eventType(), toJson(proto), event.occurredAt()));
        outboxRepo.save(new OutboxEvent(eventId, trip.getId(), event.eventType(), proto.toByteArray()));
    }

    private static String toJson(TripEvent proto) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(proto);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Cannot render trip event as JSON", e);
        }
    }

    private Trip loadTrip(UUID id) {
        return tripRepo.findById(id).orElseThrow(() -> new TripNotFoundException(id));
    }

    private void requireRider(Trip trip, UUID callerId) {
        if (!trip.getRiderId().equals(callerId)) {
            throw new TripAccessDeniedException(trip.getId());
        }
    }

    private void requireDriver(Trip trip, UUID callerId) {
        if (!callerId.equals(trip.getDriverId())) {
            throw new TripAccessDeniedException(trip.getId());
        }
    }

    private void requireParticipant(Trip trip, UUID callerId) {
        if (!trip.getRiderId().equals(callerId) && !callerId.equals(trip.getDriverId())) {
            throw new TripAccessDeniedException(trip.getId());
        }
    }
}
