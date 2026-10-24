package com.wayfare.service;

import com.wayfare.domain.GeoPoint;

import java.math.BigDecimal;

/** Prices a trip. An interface so a real pricing service can replace the local rule later. */
public interface FareEstimator {

    BigDecimal estimate(GeoPoint pickup, GeoPoint dropoff);

    /** Straight-line distance, used for the fare and as the default trip distance. */
    double distanceKm(GeoPoint pickup, GeoPoint dropoff);
}
