package com.wayfare.service;

import java.util.List;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

import com.wayfare.domain.NearbyDriver;

import lombok.RequiredArgsConstructor;

/**
 * Online drivers near a point, from the Redis geo set {@code drivers:geo:{city}}.
 * Membership in the set means "online"; nothing here knows about approval.
 */
@Component
@RequiredArgsConstructor
public class CandidateFinder {

    private final StringRedisTemplate redis;

    public static String geoKey(String city) {
        return "drivers:geo:" + city;
    }

    /** Drivers within {@code radiusKm}, nearest first, at most {@code limit}. */
    public List<NearbyDriver> find(String city, double lat, double lng, double radiusKm, int limit) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
                geoKey(city),
                GeoReference.fromCoordinate(lng, lat), // longitude first
                new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs
                    .newGeoSearchArgs()
                    .includeDistance()
                    .sortAscending()
                    .limit(limit));
        if (results == null) {
            return List.of();
        }
        return results.getContent().stream()
                .map(r -> new NearbyDriver(r.getContent().getName(), r.getDistance().getValue()))
                .toList();
    }
}
