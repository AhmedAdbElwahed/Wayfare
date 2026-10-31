package com.wayfare.service;

import java.time.Duration;
import java.util.List;

import com.wayfare.config.MatchingProperties;

/** The values from config-repo/wayfare-matching-service.yml, for tests without Spring. */
public final class TestProperties {

    private TestProperties() {
    }

    public static MatchingProperties defaults() {
        return weights(0.7, 0.3);
    }

    public static MatchingProperties weights(double etaWeight, double ratingWeight) {
        return new MatchingProperties("default", List.of(2.0, 4.0, 8.0), 20,
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofMillis(500),
                30, 4.5, etaWeight, ratingWeight);
    }
}
