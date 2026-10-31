package com.wayfare.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.wayfare.config.MatchingProperties;

import lombok.RequiredArgsConstructor;

/**
 * Two keys per match, both with the reservation TTL:
 * <ul>
 *   <li>{@code match:reserve:{driverId} -> tripId}: the lock ({@code SET NX})</li>
 *   <li>{@code match:trip:{tripId} -> driverId}: lets a trip event find its driver</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RedisDriverReservation implements DriverReservation {

    /** Delete KEYS[1] only if it still holds ARGV[1], atomically. */
    private static final RedisScript<Long> DELETE_IF_EQUALS = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final MatchingProperties properties;

    static String reserveKey(String driverId) {
        return "match:reserve:" + driverId;
    }

    static String tripKey(String tripId) {
        return "match:trip:" + tripId;
    }

    @Override
    public boolean tryReserve(String driverId, String tripId) {
        Boolean won = redis.opsForValue()
                .setIfAbsent(reserveKey(driverId), tripId, properties.reservationTtl());
        if (!Boolean.TRUE.equals(won)) {
            return false;
        }
        redis.opsForValue().set(tripKey(tripId), driverId, properties.reservationTtl());
        return true;
    }

    @Override
    public void release(String driverId, String tripId) {
        redis.execute(DELETE_IF_EQUALS, List.of(reserveKey(driverId)), tripId);
        redis.execute(DELETE_IF_EQUALS, List.of(tripKey(tripId)), driverId);
    }

    @Override
    public void releaseTrip(String tripId) {
        String driverId = redis.opsForValue().get(tripKey(tripId));
        if (driverId != null) {
            release(driverId, tripId);
        }
    }
}
