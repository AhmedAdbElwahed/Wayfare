package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.wayfare.domain.DocumentKind;
import com.wayfare.domain.DocumentStatus;
import com.wayfare.domain.Driver;
import com.wayfare.domain.DriverDocument;
import com.wayfare.domain.DriverStatus;
import com.wayfare.domain.Vehicle;
import com.wayfare.event.DriverStatusChangedEvent;
import com.wayfare.exception.InvalidDriverStateException;
import com.wayfare.repository.DriverDocumentRepository;
import com.wayfare.repository.DriverRepository;
import com.wayfare.repository.VehicleRepository;

class DriverServiceTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final DriverDocumentRepository documents = mock(DriverDocumentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DocumentStorageService storage = mock(DocumentStorageService.class);
    private final DriverService service = new DriverService(drivers, vehicles, documents, events, storage);

    private final UUID id = UUID.randomUUID();
    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = new Driver(id, "d@example.com");
        driver.setName("Dan");
        driver.setPhone("+1000000");
        when(drivers.findById(id)).thenReturn(Optional.of(driver));
        when(vehicles.findByDriverId(id)).thenReturn(Optional.of(new Vehicle()));
    }

    private DriverDocument doc(DocumentKind kind, DocumentStatus status, Instant expires) {
        DriverDocument d = new DriverDocument();
        d.setDriverId(id);
        d.setKind(kind);
        d.setStatus(status);
        d.setExpiresAt(expires);
        return d;
    }

    private List<DriverDocument> allDocs(DocumentStatus status) {
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        return java.util.Arrays.stream(DocumentKind.values()).map(k -> doc(k, status, future)).toList();
    }

    @Test
    void ensureDriverIsIdempotent() {
        when(drivers.existsById(id)).thenReturn(true);
        assertThat(service.ensureDriver(id, "x")).isFalse();
        verify(drivers, never()).save(any());
    }

    @Test
    void submitRequiresAllDocumentKinds() {
        when(documents.findByDriverId(id)).thenReturn(List.of(doc(DocumentKind.LICENSE, DocumentStatus.PENDING_REVIEW, null)));
        assertThatThrownBy(() -> service.submitForApproval(id))
                .isInstanceOf(InvalidDriverStateException.class).hasMessageContaining("Missing document");
    }

    @Test
    void submitMovesToPendingApproval() {
        when(documents.findByDriverId(id)).thenReturn(allDocs(DocumentStatus.PENDING_REVIEW));
        assertThat(service.submitForApproval(id).getStatus()).isEqualTo(DriverStatus.PENDING_APPROVAL);
    }

    @Test
    void approveNeedsVerifiedDocumentsAndPublishesEvent() {
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        when(documents.findByDriverId(id)).thenReturn(allDocs(DocumentStatus.PENDING_REVIEW));
        assertThatThrownBy(() -> service.approve(id)).isInstanceOf(InvalidDriverStateException.class);

        when(documents.findByDriverId(id)).thenReturn(allDocs(DocumentStatus.VALID));
        Driver approved = service.approve(id);

        assertThat(approved.getStatus()).isEqualTo(DriverStatus.APPROVED);
        assertThat(approved.getApprovedAt()).isNotNull();
        ArgumentCaptor<DriverStatusChangedEvent> captor = ArgumentCaptor.forClass(DriverStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("APPROVED");
    }

    @Test
    void cannotApproveOnboardingDriver() {
        assertThatThrownBy(() -> service.approve(id)).isInstanceOf(InvalidDriverStateException.class);
    }

    @Test
    void expiredValidDocumentDoesNotSatisfyApproval() {
        driver.setStatus(DriverStatus.SUSPENDED);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        when(documents.findByDriverId(id)).thenReturn(
                java.util.Arrays.stream(DocumentKind.values()).map(k -> doc(k, DocumentStatus.VALID, past)).toList());
        assertThatThrownBy(() -> service.approve(id)).isInstanceOf(InvalidDriverStateException.class);
    }

    @Test
    void expiringDocumentSuspendsApprovedDriver() {
        driver.setStatus(DriverStatus.APPROVED);
        DriverDocument d = doc(DocumentKind.LICENSE, DocumentStatus.VALID, Instant.now().minusSeconds(5));
        UUID docId = UUID.randomUUID();
        when(documents.findById(docId)).thenReturn(Optional.of(d));

        service.expireDocument(docId);

        assertThat(d.getStatus()).isEqualTo(DocumentStatus.EXPIRED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.SUSPENDED);
        ArgumentCaptor<DriverStatusChangedEvent> captor = ArgumentCaptor.forClass(DriverStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("document_expired");
    }

    @Test
    void expiringDocumentOfNonApprovedDriverOnlyExpiresDocument() {
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        DriverDocument d = doc(DocumentKind.LICENSE, DocumentStatus.VALID, Instant.now().minusSeconds(5));
        UUID docId = UUID.randomUUID();
        when(documents.findById(docId)).thenReturn(Optional.of(d));

        service.expireDocument(docId);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.PENDING_APPROVAL);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void reviewCannotMarkExpiredDocumentValid() {
        DriverDocument d = doc(DocumentKind.LICENSE, DocumentStatus.PENDING_REVIEW, Instant.now().minusSeconds(5));
        UUID docId = UUID.randomUUID();
        when(documents.findByIdAndDriverId(docId, id)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.reviewDocument(id, docId, DocumentStatus.VALID))
                .isInstanceOf(InvalidDriverStateException.class);
    }

    @Test
    void addDocumentVerifiesUploadBeforeSaving() {
        var req = new com.wayfare.dto.DocumentUploadRequest(DocumentKind.LICENSE,
                "drivers/" + id + "/documents/license-x.pdf", Instant.now().plus(10, ChronoUnit.DAYS));
        when(documents.save(any())).thenAnswer(i -> i.getArgument(0));

        DriverDocument saved = service.addDocument(id, req);

        verify(storage).verifyDocument(id, req.objectKey());
        assertThat(saved.getObjectKey()).isEqualTo(req.objectKey());
    }

    @Test
    void rejectedUploadIsNeverRecorded() {
        var req = new com.wayfare.dto.DocumentUploadRequest(DocumentKind.LICENSE,
                "drivers/someone-else/documents/x.pdf", Instant.now().plus(10, ChronoUnit.DAYS));
        org.mockito.Mockito.doThrow(new com.wayfare.storage.InvalidUploadException("nope"))
                .when(storage).verifyDocument(id, req.objectKey());

        assertThatThrownBy(() -> service.addDocument(id, req)).isInstanceOf(com.wayfare.storage.InvalidUploadException.class);
        verify(documents, never()).save(any());
    }
}
