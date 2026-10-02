package com.wayfare.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.Driver;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
}
