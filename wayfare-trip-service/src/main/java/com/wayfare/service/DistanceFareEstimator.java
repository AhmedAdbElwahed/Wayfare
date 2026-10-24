package com.wayfare.service;

import com.wayfare.domain.GeoPoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Placeholder pricing: base fee + a per-km rate on the great-circle distance.
 * There is no pricing service in the repo yet; the original design called one
 * over gRPC behind a circuit breaker.
 */
@Component
public class DistanceFareEstimator implements FareEstimator {

    private static final BigDecimal BASE_FARE = new BigDecimal("2.50");
    private static final BigDecimal PER_KM = new BigDecimal("1.20");
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public BigDecimal estimate(GeoPoint pickup, GeoPoint dropoff) {
        BigDecimal km = BigDecimal.valueOf(distanceKm(pickup, dropoff));
        return BASE_FARE.add(PER_KM.multiply(km)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Haversine formula. */
    @Override
    public double distanceKm(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
