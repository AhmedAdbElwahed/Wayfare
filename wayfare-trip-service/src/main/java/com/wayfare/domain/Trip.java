package com.wayfare.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trips")
@Getter 
@NoArgsConstructor 
public class Trip {
    @Id 
    private UUID id;
    private UUID riderId;
    private UUID driverId;
    @Enumerated(EnumType.STRING)
    private TripStatus status;
    @Embedded 
    private GeoPoint pickup;
    @Embedded 
    private GeoPoint dropoff;
    private BigDecimal fareLocked;
    private BigDecimal fareFinal;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant endedAt;
    @Version 
    private Long version;          // optimistic locking

}
