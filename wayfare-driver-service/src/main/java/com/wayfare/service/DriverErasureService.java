package com.wayfare.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.wayfare.repository.DriverRepository;
import com.wayfare.storage.ObjectStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erases everything driver-service holds for a deleted account: the driver
 * row (vehicle and document rows go with it via ON DELETE CASCADE) and every
 * uploaded document and photo in object storage. Idempotent, so Kafka
 * redelivery is harmless. Storage goes first, the database last, so a storage
 * failure leaves the rows in place and the retry still knows what to clean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverErasureService {

    private final DriverRepository drivers;
    private final ObjectStorage storage;
    private final TransactionTemplate tx;

    /** @return whether a driver row existed (false on a redelivery or for an unknown id) */
    public boolean erase(UUID driverId) {
        String prefix = "drivers/" + driverId + "/";
        int documents = storage.deleteByPrefix(storage.documentsBucket(), prefix);
        int media = storage.deleteByPrefix(storage.mediaBucket(), prefix);

        boolean existed = Boolean.TRUE.equals(tx.execute(status -> {
            if (!drivers.existsById(driverId)) {
                return false;
            }
            drivers.deleteById(driverId);
            return true;
        }));
        if (existed) {
            log.info("Erased driver {} ({} documents, {} media objects)", driverId, documents, media);
        }
        return existed;
    }
}
