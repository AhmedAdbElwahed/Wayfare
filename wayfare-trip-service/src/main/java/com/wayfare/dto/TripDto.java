package com.wayfare.dto;

import com.wayfare.domain.GeoPoint;
import com.wayfare.domain.Trip;

import java.math.BigDecimal;
import java.util.UUID;

/** What the command side returns right after a write (the read model is eventually consistent). */
public record TripDto(UUID id, String status, GeoPoint pickup, GeoPoint dropoff, BigDecimal fareLocked) {

    public static TripDto from(Trip trip) {
        return new TripDto(trip.getId(), trip.getStatus().name(), trip.getPickup(),
                trip.getDropoff(), trip.getFareLocked());
    }
}
