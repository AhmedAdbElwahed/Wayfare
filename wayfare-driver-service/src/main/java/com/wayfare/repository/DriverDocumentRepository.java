package com.wayfare.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.DocumentStatus;
import com.wayfare.domain.DriverDocument;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriverId(UUID driverId);

    Optional<DriverDocument> findByIdAndDriverId(UUID id, UUID driverId);

    List<DriverDocument> findByExpiresAtBeforeAndStatus(Instant cutoff, DocumentStatus status);
}
