package com.wayfare.domain;

/**
 * A driver the scorer ranks. {@code averageRating} is null when the driver has
 * no ratings yet; the scorer then uses the configured default.
 */
public record Candidate(String driverId, double distanceKm, Double averageRating) {
}
