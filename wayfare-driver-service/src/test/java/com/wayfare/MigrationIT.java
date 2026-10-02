package com.wayfare;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.wayfare.domain.Driver;
import com.wayfare.domain.DriverStatus;
import com.wayfare.repository.DriverRepository;

/** Runs Flyway against real Postgres, then lets Hibernate validate the entities against the result. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MigrationIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DriverRepository drivers;

    @Test
    void schemaMatchesEntitiesAndDriversPersist() {
        UUID id = UUID.randomUUID();
        drivers.saveAndFlush(new Driver(id, "d@example.com"));
        assertThat(drivers.findById(id)).get().extracting(Driver::getStatus).isEqualTo(DriverStatus.ONBOARDING);
    }
}
