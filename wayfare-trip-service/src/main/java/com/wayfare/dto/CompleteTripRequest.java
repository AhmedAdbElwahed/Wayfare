package com.wayfare.dto;

import jakarta.validation.constraints.Positive;

/** Both optional: when absent the service uses the straight-line distance and the locked fare. */
public record CompleteTripRequest(@Positive Double distanceKm) {
}
