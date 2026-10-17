package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wayfare.event.AccountDeletedEvent;
import com.wayfare.event.AccountEventsConsumer;
import com.wayfare.repository.DriverRepository;
import com.wayfare.storage.ObjectStorage;

class DriverErasureServiceTest {

    DriverRepository drivers = mock(DriverRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    DriverErasureService service = new DriverErasureService(drivers, storage,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    UUID id = UUID.randomUUID();

    DriverErasureServiceTest() {
        when(storage.documentsBucket()).thenReturn("docs");
        when(storage.mediaBucket()).thenReturn("media");
    }

    @Test
    void erasesRowAndBothBucketPrefixes() {
        when(drivers.existsById(id)).thenReturn(true);

        assertThat(service.erase(id)).isTrue();

        verify(storage).deleteByPrefix("docs", "drivers/" + id + "/");
        verify(storage).deleteByPrefix("media", "drivers/" + id + "/");
        verify(drivers).deleteById(id);
    }

    @Test
    void redeliveryIsANoOp() {
        when(drivers.existsById(id)).thenReturn(false);

        assertThat(service.erase(id)).isFalse();
        verify(drivers, never()).deleteById(any());
    }

    @Test
    void consumerIgnoresOtherRoles() {
        DriverErasureService erasure = mock(DriverErasureService.class);
        var consumer = new AccountEventsConsumer().onAccountDeleted(erasure);

        consumer.accept(AccountDeletedEvent.newBuilder().setAccountId(id.toString()).setRole("RIDER").build());
        verify(erasure, never()).erase(any());

        consumer.accept(AccountDeletedEvent.newBuilder().setAccountId(id.toString()).setRole("DRIVER").build());
        verify(erasure).erase(id);
    }
}
