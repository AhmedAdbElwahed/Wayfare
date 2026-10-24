package com.wayfare.domain;

import com.wayfare.repository.OutboxRepository;
import com.wayfare.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migration against PostgreSQL and checks the pieces the
 * outbox pattern depends on: jsonb mapping, publish ordering, and the status
 * filter. Needs Docker.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OutboxRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OutboxRepository outbox;
    @Autowired TripRepository trips;

    @Test
    void tripRoundTripsAndOptimisticVersionStartsAtZero() {
        Trip trip = trips.saveAndFlush(Trip.create(UUID.randomUUID(), new GeoPoint(1, 2),
                new GeoPoint(3, 4), new BigDecimal("5.00")));
        Trip loaded = trips.findById(trip.getId()).orElseThrow();
        assertThat(loaded.getDropoff()).isEqualTo(new GeoPoint(3, 4));
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void pendingQueryReturnsOnlyUnpublishedRowsOldestFirst() throws InterruptedException {
        UUID agg = UUID.randomUUID();
        OutboxEvent first = outbox.saveAndFlush(new OutboxEvent(UUID.randomUUID(), agg, "A", new byte[]{1, 2}));
        Thread.sleep(5);
        outbox.saveAndFlush(new OutboxEvent(UUID.randomUUID(), agg, "B", new byte[]{3}));
        first.markPublished();
        outbox.saveAndFlush(first);

        var pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(pending).extracting(OutboxEvent::getEventType).containsExactly("B");
        assertThat(pending.get(0).getPayload()).containsExactly(3); // bytea round trip
    }
}
