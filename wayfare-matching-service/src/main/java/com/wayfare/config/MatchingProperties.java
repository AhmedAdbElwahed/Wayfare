package com.wayfare.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code wayfare.matching.*} in config-repo/wayfare-matching-service.yml. */
@ConfigurationProperties(prefix = "wayfare.matching")
public record MatchingProperties(
    String city,
    List<Double> radiiKm,
    int candidateLimit,
    Duration reservationTtl,
    Duration dispatchGuardTtl,
    Duration driverLookupTimeout,
    double avgSpeedKmh,
    double defaultRating,
    double etaWeight,
    double ratingWeight
) {

    /** BatchGetDrivers rejects more ids than this in one call. */
    public static final int MAX_DRIVER_BATCH = 100;

    public MatchingProperties {
        if (radiiKm == null || radiiKm.isEmpty()) {
            throw new IllegalArgumentException("wayfare.matching.radii-km must not be empty");
        }
        if (candidateLimit < 1 || candidateLimit > MAX_DRIVER_BATCH) {
            throw new IllegalArgumentException(
                    "wayfare.matching.candidate-limit must be 1.." + MAX_DRIVER_BATCH);
        }
        if (avgSpeedKmh <= 0) {
            throw new IllegalArgumentException("wayfare.matching.avg-speed-kmh must be positive");
        }
    }
}
