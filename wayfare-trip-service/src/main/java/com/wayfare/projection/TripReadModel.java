package com.wayfare.projection;

import com.wayfare.domain.GeoPoint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The query side's document: one per trip, shaped for reading (status, timeline,
 * participants in one place) rather than for writing. It is only ever changed by
 * {@link TripProjector}; nothing in the command path touches MongoDB.
 */
@Document(collection = "trips_read")
@Getter
@Setter
@NoArgsConstructor
public class TripReadModel {

    @Id
    private String id; // tripId

    @Indexed
    private String riderId;
    @Indexed
    private String driverId;

    private String status;
    private GeoPoint pickup;
    private GeoPoint dropoff;
    private BigDecimal fareLocked;
    private BigDecimal fareFinal;
    private Double distanceKm;
    private List<TimelineEntry> timeline = new ArrayList<>();
    private Instant createdAt;

    /** eventIds already applied; makes redelivered events no-ops. */
    private Set<String> processedEventIds = new HashSet<>();

    public record TimelineEntry(String status, Instant at) {
    }
}
