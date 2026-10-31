package com.wayfare.dev;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.wayfare.config.MatchingProperties;
import com.wayfare.service.CandidateFinder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev only: nothing writes driver positions until location ingestion exists, so
 * put the configured drivers in the geo set, 0.5 km apart going north.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DriverSeeder implements ApplicationRunner {

    /** About 0.5 km of latitude. */
    private static final double STEP_DEGREES = 0.0045;

    private final StringRedisTemplate redis;
    private final MatchingProperties matching;
    private final SeedProperties seed;

    @ConfigurationProperties(prefix = "wayfare.matching.seed")
    public record SeedProperties(List<String> driverIds, double lat, double lng) {
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> ids = seed.driverIds() == null ? List.of() : seed.driverIds();
        if (ids.isEmpty()) {
            log.warn("dev profile but wayfare.matching.seed.driver-ids is empty; no drivers seeded");
            return;
        }
        String key = CandidateFinder.geoKey(matching.city());
        for (int i = 0; i < ids.size(); i++) {
            // Point is (x = longitude, y = latitude).
            redis.opsForGeo().add(key, new Point(seed.lng(), seed.lat() + (i + 1) * STEP_DEGREES), ids.get(i));
        }
        log.info("Seeded {} drivers into {} near ({}, {})", ids.size(), key, seed.lat(), seed.lng());
    }
}
