package com.wayfare.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wayfare.domain.DocumentKind;
import com.wayfare.domain.DocumentStatus;
import com.wayfare.domain.Driver;
import com.wayfare.domain.DriverDocument;
import com.wayfare.domain.DriverStatus;
import com.wayfare.domain.Vehicle;
import com.wayfare.dto.DocumentUploadRequest;
import com.wayfare.dto.UpdateDriverProfileRequest;
import com.wayfare.dto.VehicleRequest;
import com.wayfare.event.DriverStatusChangedEvent;
import com.wayfare.exception.DocumentNotFoundException;
import com.wayfare.exception.DriverNotFoundException;
import com.wayfare.exception.InvalidDriverStateException;
import com.wayfare.exception.VehicleNotFoundException;
import com.wayfare.repository.DriverDocumentRepository;
import com.wayfare.repository.DriverRepository;
import com.wayfare.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Driver lifecycle: ONBOARDING → PENDING_APPROVAL → APPROVED ⇄ SUSPENDED.
 * Status changes that matter to other services publish a
 * {@link DriverStatusChangedEvent}, delivered after commit.
 */
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverDocumentRepository documentRepository;
    private final ApplicationEventPublisher events;
    private final DocumentStorageService storageService;

    /** Idempotent: returns whether a row was written (false on event redelivery). */
    @Transactional
    public boolean ensureDriver(UUID id, String email) {
        if (driverRepository.existsById(id)) {
            return false;
        }
        driverRepository.save(new Driver(id, email));
        return true;
    }

    @Transactional(readOnly = true)
    public Driver get(UUID id) {
        return driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    @Transactional
    public Driver updateProfile(UUID id, UpdateDriverProfileRequest request) {
        Driver driver = get(id);
        if (request.name() != null) {
            driver.setName(request.name());
        }
        if (request.phone() != null) {
            driver.setPhone(request.phone());
        }
        // Flush so a duplicate phone surfaces here as the unique-index violation
        // (→ 409) instead of at commit, after the handler has returned.
        return driverRepository.saveAndFlush(driver);
    }

    @Transactional
    public Vehicle upsertVehicle(UUID driverId, VehicleRequest request) {
        get(driverId);
        Vehicle vehicle = vehicleRepository.findByDriverId(driverId).orElseGet(Vehicle::new);
        vehicle.setDriverId(driverId);
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setPlate(request.plate());
        vehicle.setColor(request.color());
        vehicle.setCapacity(request.capacity());
        vehicle.setType(request.type());
        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicle(UUID driverId) {
        return vehicleRepository.findByDriverId(driverId).orElseThrow(() -> new VehicleNotFoundException(driverId));
    }

    @Transactional
    public DriverDocument addDocument(UUID driverId, DocumentUploadRequest request) {
        get(driverId);
        // Checks the object exists, belongs to this driver's prefix and is a sane size/type.
        storageService.verifyDocument(driverId, request.objectKey());
        DriverDocument doc = new DriverDocument();
        doc.setDriverId(driverId);
        doc.setKind(request.kind());
        doc.setObjectKey(request.objectKey());
        doc.setExpiresAt(request.expiresAt());
        return documentRepository.save(doc);
    }

    @Transactional
    public Driver setPhoto(UUID driverId, String objectKey) {
        Driver driver = get(driverId);
        driver.setPhotoUrl(storageService.verifyPhoto(driverId, objectKey));
        return driver;
    }

    @Transactional(readOnly = true)
    public List<DriverDocument> listDocuments(UUID driverId) {
        return documentRepository.findByDriverId(driverId);
    }

    /** Driver hands the application over for review. */
    @Transactional
    public Driver submitForApproval(UUID driverId) {
        Driver driver = get(driverId);
        if (driver.getStatus() != DriverStatus.ONBOARDING) {
            throw new InvalidDriverStateException("Only an onboarding driver can submit; current status is " + driver.getStatus());
        }
        if (driver.getName() == null || driver.getPhone() == null) {
            throw new InvalidDriverStateException("Name and phone are required before submitting");
        }
        if (vehicleRepository.findByDriverId(driverId).isEmpty()) {
            throw new InvalidDriverStateException("A vehicle is required before submitting");
        }
        List<DriverDocument> docs = documentRepository.findByDriverId(driverId);
        for (DocumentKind kind : DocumentKind.REQUIRED) {
            if (docs.stream().noneMatch(d -> d.getKind() == kind)) {
                throw new InvalidDriverStateException("Missing document: " + kind);
            }
        }
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        return driver;
    }

    /** Admin verdict on one document. */
    @Transactional
    public DriverDocument reviewDocument(UUID driverId, UUID documentId, DocumentStatus verdict) {
        if (verdict != DocumentStatus.VALID && verdict != DocumentStatus.REJECTED) {
            throw new InvalidDriverStateException("A review can only mark a document VALID or REJECTED");
        }
        DriverDocument doc = documentRepository.findByIdAndDriverId(documentId, driverId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (verdict == DocumentStatus.VALID && doc.getExpiresAt() != null && !doc.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidDriverStateException("Document has already expired");
        }
        doc.setStatus(verdict);
        return doc;
    }

    /**
     * Approves an application (or reinstates a suspended driver). Requires the
     * vehicle and a VALID, unexpired document of every required kind, so
     * reinstatement after an expiry can't skip re-verification.
     */
    @Transactional
    public Driver approve(UUID driverId) {
        Driver driver = get(driverId);
        if (driver.getStatus() != DriverStatus.PENDING_APPROVAL && driver.getStatus() != DriverStatus.SUSPENDED) {
            throw new InvalidDriverStateException("Cannot approve a driver in status " + driver.getStatus());
        }
        if (vehicleRepository.findByDriverId(driverId).isEmpty()) {
            throw new InvalidDriverStateException("Driver has no vehicle");
        }
        Instant now = Instant.now();
        List<DriverDocument> docs = documentRepository.findByDriverId(driverId);
        for (DocumentKind kind : DocumentKind.REQUIRED) {
            boolean ok = docs.stream().anyMatch(d -> d.getKind() == kind
                    && d.getStatus() == DocumentStatus.VALID
                    && (d.getExpiresAt() == null || d.getExpiresAt().isAfter(now)));
            if (!ok) {
                throw new InvalidDriverStateException("No valid, unexpired document of kind " + kind);
            }
        }
        driver.setStatus(DriverStatus.APPROVED);
        driver.setApprovedAt(now);
        events.publishEvent(new DriverStatusChangedEvent(driverId, DriverStatus.APPROVED.name(), null, now));
        return driver;
    }

    @Transactional
    public Driver suspend(UUID driverId, String reason) {
        Driver driver = get(driverId);
        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            return driver; // already suspended: keep it idempotent
        }
        driver.setStatus(DriverStatus.SUSPENDED);
        events.publishEvent(new DriverStatusChangedEvent(driverId, DriverStatus.SUSPENDED.name(), reason, Instant.now()));
        return driver;
    }

    /** Marks one VALID document expired and suspends an approved driver. Used by the expiry job. */
    @Transactional
    public void expireDocument(UUID documentId) {
        DriverDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (doc.getStatus() != DocumentStatus.VALID) {
            return; // already handled by a concurrent run
        }
        doc.setStatus(DocumentStatus.EXPIRED);
        Driver driver = get(doc.getDriverId());
        if (driver.getStatus() == DriverStatus.APPROVED) {
            suspend(driver.getId(), "document_expired");
        }
    }
}
