package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.wayfare.domain.NearbyDriver;

/** Geo search, reservations and the dispatch guard against a real Redis. */
@Testcontainers
class RedisMatchingIT {

    // Tahrir Square, Cairo. A real place, so a swapped lat/lng would visibly break the distances.
    static final double LAT = 30.0444, LNG = 31.2357;

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    CandidateFinder finder;
    RedisDriverReservation reservation;
    DispatchGuard guard;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(redisContainer.getHost(), redisContainer.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        finder = new CandidateFinder(redis);
        reservation = new RedisDriverReservation(redis, TestProperties.defaults());
        guard = new DispatchGuard(redis, TestProperties.defaults());
    }

    @Test
    void radiusLimitsResultsAndNearestComesFirst() {
        // 0.009 degrees of latitude is about 1 km; 0.045 about 5 km.
        redis.opsForGeo().add("drivers:geo:default", new Point(LNG, LAT + 0.045), "far");
        redis.opsForGeo().add("drivers:geo:default", new Point(LNG, LAT + 0.009), "near");

        List<NearbyDriver> small = finder.find("default", LAT, LNG, 2, 20);
        List<NearbyDriver> large = finder.find("default", LAT, LNG, 8, 20);

        assertThat(small).extracting(NearbyDriver::driverId).containsExactly("near");
        assertThat(small.get(0).distanceKm()).isBetween(0.9, 1.1);
        assertThat(large).extracting(NearbyDriver::driverId).containsExactly("near", "far");
        assertThat(large.get(1).distanceKm()).isBetween(4.8, 5.2);
    }

    @Test
    void otherCityIsNotSearched() {
        redis.opsForGeo().add("drivers:geo:elsewhere", new Point(LNG, LAT), "d");

        assertThat(finder.find("default", LAT, LNG, 8, 20)).isEmpty();
    }

    @Test
    void secondReservationOfTheSameDriverFails() {
        assertThat(reservation.tryReserve("d1", "t1")).isTrue();
        assertThat(reservation.tryReserve("d1", "t2")).isFalse();
        assertThat(redis.getExpire("match:reserve:d1")).isPositive();
    }

    @Test
    void exactlyOneOfManyRacersWins() throws Exception {
        int racers = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, racers)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        start.await();
                        return reservation.tryReserve("d1", "t" + i);
                    }).toList();
            List<Future<Boolean>> results = calls.stream().map(pool::submit).toList();
            start.countDown();

            long winners = 0;
            for (Future<Boolean> f : results) {
                winners += f.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void releaseDoesNotFreeSomeoneElsesReservation() {
        reservation.tryReserve("d1", "t1");

        reservation.release("d1", "t2");

        assertThat(redis.opsForValue().get("match:reserve:d1")).isEqualTo("t1");
    }

    @Test
    void releaseTripFreesItsDriver() {
        reservation.tryReserve("d1", "t1");

        reservation.releaseTrip("t1");

        assertThat(redis.hasKey("match:reserve:d1")).isFalse();
        assertThat(redis.hasKey("match:trip:t1")).isFalse();
        assertThat(reservation.tryReserve("d1", "t2")).isTrue();
    }

    @Test
    void releaseTripWithoutAMatchIsANoOp() {
        reservation.releaseTrip("unknown");
    }

    @Test
    void guardLetsOnlyTheFirstDispatchThroughUntilReleased() {
        assertThat(guard.tryBegin("t1")).isTrue();
        assertThat(guard.tryBegin("t1")).isFalse();

        guard.release("t1");

        assertThat(guard.tryBegin("t1")).isTrue();
    }
}
