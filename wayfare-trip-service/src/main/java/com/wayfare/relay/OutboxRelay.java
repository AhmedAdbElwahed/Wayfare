package com.wayfare.relay;

import com.google.protobuf.InvalidProtocolBufferException;
import com.wayfare.domain.OutboxEvent;
import com.wayfare.event.TripEvent;
import com.wayfare.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Moves outbox rows to Kafka. Polls every 500 ms; each run publishes up to 100
 * unpublished rows in creation order and stamps them {@code publishedAt}.
 *
 * <p>Delivery is <b>at-least-once</b>: if the process dies after Kafka accepted a
 * message but before the stamp commits, the row is sent again on the next run.
 * That is why the projector must be idempotent (it dedups on the {@code eventId} header).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final String BINDING = "tripEvents-out-0";

    private final OutboxRepository outboxRepo;
    private final StreamBridge streamBridge;

    /**
     * The outbox stores plain protobuf bytes; the Confluent serializer wants the
     * message object (it derives the schema from it and registers it).
     */
    private static TripEvent parse(OutboxEvent evt) {
        try {
            return TripEvent.parseFrom(evt.getPayload());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Corrupt outbox payload " + evt.getId(), e);
        }
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxRepo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent evt : pending) {
            Message<TripEvent> message = MessageBuilder
                    .withPayload(parse(evt))
                    // Same key => same partition => one trip's events stay in order.
                    .setHeader(KafkaHeaders.KEY, evt.getAggregateId().toString())
                    .setHeader("eventType", evt.getEventType())
                    // Lets consumers recognise a redelivery of the same event.
                    .setHeader("eventId", evt.getId().toString())
                    .build();

            boolean sent;
            try {
                sent = streamBridge.send(BINDING, message);
            } catch (RuntimeException ex) {
                // Producer is in sync mode, so a broker failure surfaces here.
                log.warn("Kafka send threw for outbox event {}", evt.getId(), ex);
                sent = false;
            }
            if (!sent) {
                // Stop at the first failure: sending later rows first would reorder
                // the trip's events. Returning (not rethrowing) lets the rows already
                // marked published commit; the rest stay unpublished and are retried.
                log.warn("Kafka send failed for outbox event {}; will retry", evt.getId());
                return;
            }
            evt.markPublished(); // flushed when the transaction commits
        }
    }
}
