package com.wayfare.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByDriverId(UUID driverId);

    List<Vehicle> findByDriverIdIn(Collection<UUID> driverIds);
}
