package com.wayfare.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.wayfare.config.MatchingProperties;

import lombok.RequiredArgsConstructor;

/**
 * Kafka delivers at least once. {@code dispatching:{tripId}} makes sure a
 * redelivered RideRequested is dispatched only once.
 */
@Component
@RequiredArgsConstructor
public class DispatchGuard {

    private final StringRedisTemplate redis;
    private final MatchingProperties properties;

    static String key(String tripId) {
        return "dispatching:" + tripId;
    }

    /** True if this call won the right to dispatch {@code tripId}. */
    public boolean tryBegin(String tripId) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key(tripId), "1", properties.dispatchGuardTtl()));
    }

    /** Lets a later delivery of {@code tripId} dispatch again (after a failure). */
    public void release(String tripId) {
        redis.delete(key(tripId));
    }
}
