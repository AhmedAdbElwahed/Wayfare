package com.wayfare.event;

import java.time.Instant;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Publishes the matching outcome to {@code match.events}, keyed by trip id. */
@Component
@RequiredArgsConstructor
public class MatchEventPublisher {

    static final String BINDING = "matchEvents-out-0";

    private final StreamBridge streamBridge;

    public void driverMatched(String tripId, String driverId) {
        send(envelope(tripId)
                .setDriverMatched(DriverMatched.newBuilder().setDriverId(driverId))
                .build());
    }

    public void noDriversAvailable(String tripId) {
        send(envelope(tripId)
                .setNoDriversAvailable(NoDriversAvailable.getDefaultInstance())
                .build());
    }

    private static MatchEvent.Builder envelope(String tripId) {
        return MatchEvent.newBuilder()
                .setTripId(tripId)
                .setOccurredAt(ProtoTimes.toTimestamp(Instant.now()));
    }

    private void send(MatchEvent event) {
        Message<MatchEvent> message = MessageBuilder
                .withPayload(event)
                // Same key => same partition => one trip's outcomes stay in order.
                .setHeader(KafkaHeaders.KEY, event.getTripId())
                .build();
        // The producer is in sync mode, so a broker failure throws from send().
        if (!streamBridge.send(BINDING, message)) {
            throw new IllegalStateException("Kafka send failed for match event of trip " + event.getTripId());
        }
    }
}
