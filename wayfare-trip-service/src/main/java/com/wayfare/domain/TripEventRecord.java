package com.wayfare.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only audit log of everything that ever happened to a
 * trip. Never updated or deleted; {@code trips} says where a trip is now, this
 * table says how it got there.
 */
@Entity
@Table(name = "trip_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripEventRecord {

    @Id
    private UUID id;
    private UUID tripId;
    private String type;

    @JdbcTypeCode(SqlTypes.JSON) // stored in the jsonb column
    private String payload;

    private Instant occurredAt;

    public TripEventRecord(UUID tripId, String type, String payload, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.type = type;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
