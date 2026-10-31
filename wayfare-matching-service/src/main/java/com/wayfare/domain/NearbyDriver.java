package com.wayfare.domain;

/** One geo search hit: an online driver and how far they are from the pickup. */
public record NearbyDriver(String driverId, double distanceKm) {
}
