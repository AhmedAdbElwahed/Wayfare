package com.wayfare.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
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
import com.wayfare.domain.RideType;
import com.wayfare.domain.Vehicle;

/** The batch gRPC path leans on findByDriverIdIn; check it against real Postgres. Needs Docker. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class VehicleRepositoryIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;

    @Test
    void findByDriverIdInReturnsOnlyTheRequestedDriversVehicles() {
        UUID a = saveDriverWithVehicle("A-1");
        UUID b = saveDriverWithVehicle("B-2");
        saveDriverWithVehicle("C-3");
        UUID noVehicle = UUID.randomUUID();
        drivers.saveAndFlush(new Driver(noVehicle, noVehicle + "@example.com"));

        List<Vehicle> found = vehicles.findByDriverIdIn(Set.of(a, b, noVehicle, UUID.randomUUID()));

        assertThat(found).extracting(Vehicle::getDriverId).containsExactlyInAnyOrder(a, b);
        assertThat(found).extracting(Vehicle::getType).containsOnly(RideType.ECONOMY);
    }

    private UUID saveDriverWithVehicle(String plate) {
        UUID id = UUID.randomUUID();
        drivers.saveAndFlush(new Driver(id, id + "@example.com"));
        Vehicle v = new Vehicle();
        v.setDriverId(id);
        v.setMake("Toyota");
        v.setModel("Corolla");
        v.setPlate(plate);
        v.setColor("white");
        v.setCapacity(4);
        v.setType(RideType.ECONOMY);
        vehicles.saveAndFlush(v);
        return id;
    }
}
