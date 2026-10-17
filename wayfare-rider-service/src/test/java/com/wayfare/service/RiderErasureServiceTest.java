package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wayfare.domain.PaymentMethod;
import com.wayfare.event.AccountDeletedEvent;
import com.wayfare.event.AccountEventsConsumer;
import com.wayfare.repository.HistoryIndexRepository;
import com.wayfare.repository.PaymentMethodRepository;
import com.wayfare.repository.ProfileRepository;
import com.wayfare.storage.ObjectStorage;

class RiderErasureServiceTest {

    ProfileRepository profiles = mock(ProfileRepository.class);
    PaymentMethodRepository payments = mock(PaymentMethodRepository.class);
    HistoryIndexRepository history = mock(HistoryIndexRepository.class);
    PaymentTokenRevoker revoker = mock(PaymentTokenRevoker.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    CacheManager caches = mock(CacheManager.class);
    Cache ridersCache = mock(Cache.class);
    RiderErasureService service = new RiderErasureService(profiles, payments, history, revoker, storage, caches,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    UUID id = UUID.randomUUID();

    RiderErasureServiceTest() {
        when(storage.mediaBucket()).thenReturn("media");
        when(caches.getCache("riders")).thenReturn(ridersCache);
    }

    @Test
    void erasesEverythingAndEvictsCache() {
        when(payments.findByUserId(id)).thenReturn(List.of(
                new PaymentMethod(UUID.randomUUID(), id, "tok-1", "visa", "4242", true, 0L)));
        when(payments.deleteAllByUserId(id)).thenReturn(1);
        when(history.deleteAllByUserId(id)).thenReturn(2);
        when(profiles.existsById(id)).thenReturn(true);

        assertThat(service.erase(id)).isTrue();

        verify(revoker).revoke("tok-1");
        verify(storage).deleteByPrefix("media", "riders/" + id + "/");
        verify(profiles).deleteById(id);
        verify(ridersCache).evict(id);
    }

    @Test
    void secondRunFindsNothingAndReturnsFalse() {
        when(payments.findByUserId(id)).thenReturn(List.of());

        assertThat(service.erase(id)).isFalse();
        verify(revoker, never()).revoke(anyString());
    }

    @Test
    void providerFailureLeavesDatabaseRowsForTheRetry() {
        when(payments.findByUserId(id)).thenReturn(List.of(
                new PaymentMethod(UUID.randomUUID(), id, "tok-1", "visa", "4242", true, 0L)));
        org.mockito.Mockito.doThrow(new IllegalStateException("provider down")).when(revoker).revoke("tok-1");

        assertThatThrownBy(() -> service.erase(id)).isInstanceOf(IllegalStateException.class);

        verify(payments, never()).deleteAllByUserId(any());
        verify(profiles, never()).deleteById(any());
    }

    @Test
    void consumerIgnoresOtherRoles() {
        RiderErasureService erasure = mock(RiderErasureService.class);
        var consumer = new AccountEventsConsumer().onAccountDeleted(erasure);

        consumer.accept(AccountDeletedEvent.newBuilder().setAccountId(id.toString()).setRole("DRIVER").build());
        verify(erasure, never()).erase(any());

        consumer.accept(AccountDeletedEvent.newBuilder().setAccountId(id.toString()).setRole("RIDER").build());
        verify(erasure).erase(id);
    }
}
