package com.wayfare.job;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wayfare.domain.DocumentStatus;
import com.wayfare.repository.DriverDocumentRepository;
import com.wayfare.service.DriverService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily sweep for lapsed documents. Each document is expired in its own
 * transaction (through {@link DriverService}) so one bad row can't roll back
 * the rest. With several replicas every instance runs this; the per-document
 * status check makes the overlap harmless, and a scheduler lock can be added
 * if the duplicate scans ever matter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentExpiryJob {

    private final DriverDocumentRepository documentRepository;
    private final DriverService driverService;

    @Scheduled(cron = "${wayfare.driver.document-expiry-cron:0 0 2 * * *}")
    public void checkExpiredDocuments() {
        var expired = documentRepository.findByExpiresAtBeforeAndStatus(Instant.now(), DocumentStatus.VALID);
        for (var doc : expired) {
            try {
                driverService.expireDocument(doc.getId());
            } catch (RuntimeException ex) {
                log.error("Failed to expire document {}", doc.getId(), ex);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} document(s)", expired.size());
        }
    }
}
