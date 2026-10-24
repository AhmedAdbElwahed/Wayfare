package com.wayfare.service;

import com.wayfare.domain.GeoPoint;
import com.wayfare.domain.IllegalStateTransitionException;
import com.wayfare.domain.OutboxEvent;
import com.wayfare.domain.Trip;
import com.wayfare.domain.TripEventRecord;
import com.wayfare.exception.TripAccessDeniedException;
import com.wayfare.repository.OutboxRepository;
import com.wayfare.repository.TripEventRepository;
import com.wayfare.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.wayfare.event.TripEvent;
import com.wayfare.event.TripEventMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCommandServiceTest {

    private final TripRepository tripRepo = mock(TripRepository.class);
    private final TripEventRepository eventRepo = mock(TripEventRepository.class);
    private final OutboxRepository outboxRepo = mock(OutboxRepository.class);
    private TripCommandService service;

    private final UUID rider = UUID.randomUUID();
    private Trip trip;

    @BeforeEach
    void setUp() {
        service = new TripCommandService(tripRepo, eventRepo, outboxRepo,
                new DistanceFareEstimator(), new TripEventMapper());
        trip = Trip.create(rider, new GeoPoint(30, 31), new GeoPoint(30.1, 31.1), new BigDecimal("9.99"));
        when(tripRepo.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(tripRepo.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void confirmWritesStateEventLogAndOutboxRowTogether() throws Exception {
        service.confirmTrip(trip.getId(), rider);

        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(outbox.capture());
        verify(tripRepo).save(trip);

        assertThat(outbox.getValue().getEventType()).isEqualTo("RideRequested");
        assertThat(outbox.getValue().getAggregateId()).isEqualTo(trip.getId());
        // The outbox holds the binary protobuf; its event id is the row id.
        TripEvent wire = TripEvent.parseFrom(outbox.getValue().getPayload());
        assertThat(wire.getTripId()).isEqualTo(trip.getId().toString());
        assertThat(wire.getEventId()).isEqualTo(outbox.getValue().getId().toString());
        assertThat(wire.hasRideRequested()).isTrue();
        assertThat(wire.getRideRequested().getFareLocked()).isEqualTo("9.99");

        // The audit log keeps the readable JSON form of the same event.
        ArgumentCaptor<TripEventRecord> log = ArgumentCaptor.forClass(TripEventRecord.class);
        verify(eventRepo).save(log.capture());
        assertThat(log.getValue().getPayload()).contains("\"rideRequested\"", trip.getId().toString());
        assertThat(outbox.getValue().getPublishedAt()).isNull();
    }

    @Test
    void someoneElsesTripCannotBeConfirmed() {
        assertThatThrownBy(() -> service.confirmTrip(trip.getId(), UUID.randomUUID()))
                .isInstanceOf(TripAccessDeniedException.class);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void failedTransitionWritesNoEvent() {
        assertThatThrownBy(() -> service.applyDriverMatched(trip.getId(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateTransitionException.class);
        verify(outboxRepo, never()).save(any());
        verify(eventRepo, never()).save(any());
    }
}
