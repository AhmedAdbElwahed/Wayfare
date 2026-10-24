package com.wayfare.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A message waiting to be sent to Kafka. It is inserted in the same database
 * transaction as the {@link Trip} change, so "state changed" and "event will be
 * sent" commit or roll back together. {@code publishedAt == null} means the
 * relay has not delivered it yet.
 */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;
    private UUID aggregateId;
    private String eventType;

    /** Serialized protobuf {@code TripEvent}. */
    private byte[] payload;

    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent(UUID id, UUID aggregateId, String eventType, byte[] payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }
}
