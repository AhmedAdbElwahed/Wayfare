package com.wayfare.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.wayfare.domain.PaymentMethod;
import com.wayfare.repository.HistoryIndexRepository;
import com.wayfare.repository.PaymentMethodRepository;
import com.wayfare.repository.ProfileRepository;
import com.wayfare.storage.ObjectStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erases everything rider-service holds for a deleted account. Idempotent:
 * running it again after success finds nothing and returns false, which is
 * what makes Kafka redelivery harmless.
 *
 * <p>Order matters. External side effects (provider tokens, photo objects) go
 * first and the database rows last, in one transaction: if the provider call
 * fails the rows - and with them the tokens needed to retry - are still there
 * when the message is redelivered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderErasureService {

    private final ProfileRepository profiles;
    private final PaymentMethodRepository payments;
    private final HistoryIndexRepository history;
    private final PaymentTokenRevoker tokenRevoker;
    private final ObjectStorage storage;
    private final CacheManager cacheManager;
    private final TransactionTemplate tx;

    /** @return whether any rider data existed (false on a redelivery or for an unknown id) */
    public boolean erase(UUID riderId) {
        List<PaymentMethod> methods = payments.findByUserId(riderId);
        methods.forEach(m -> tokenRevoker.revoke(m.getProviderToken()));
        storage.deleteByPrefix(storage.mediaBucket(), "riders/" + riderId + "/");

        boolean existed = Boolean.TRUE.equals(tx.execute(status -> {
            int paymentRows = payments.deleteAllByUserId(riderId);
            int historyRows = history.deleteAllByUserId(riderId);
            boolean hadProfile = profiles.existsById(riderId);
            profiles.deleteById(riderId);
            return hadProfile || paymentRows > 0 || historyRows > 0;
        }));

        Cache cache = cacheManager.getCache("riders");
        if (cache != null) {
            cache.evict(riderId);
        }
        if (existed) {
            log.info("Erased rider {} ({} payment methods)", riderId, methods.size());
        }
        return existed;
    }
}
