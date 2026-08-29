package com.wayfare.service;

import com.wayfare.domain.PaymentMethod;
import com.wayfare.dto.AddPaymentMethodRequest;
import com.wayfare.exception.PaymentMethodConflictException;
import com.wayfare.exception.PaymentMethodNotFoundException;
import com.wayfare.repository.PaymentMethodRepository;
import com.wayfare.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public List<PaymentMethod> list(UUID userId) {
        return paymentMethodRepository.findByUserId(userId);
    }

    @Transactional
    public PaymentMethod addPaymentMethod(UUID userId, AddPaymentMethodRequest request) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setUserId(userId);
        paymentMethod.setProviderToken(request.providerToken());
        paymentMethod.setBrand(request.brand());
        paymentMethod.setLast4(request.last4());
        paymentMethod.setDefault(request.isDefault());

        if (request.isDefault()) {
            clearExistingDefault(userId);
        }
        PaymentMethod saved = saveDefaultSafely(paymentMethod);
        if (saved.isDefault()) {
            syncProfileDefault(userId, saved.getId());
        }
        return saved;
    }

    @Transactional
    public PaymentMethod setDefault(UUID userId, UUID paymentMethodId) {
        PaymentMethod target = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(paymentMethodId));
        clearExistingDefault(userId);
        target.setDefault(true);
        target = saveDefaultSafely(target);
        syncProfileDefault(userId, target.getId());
        return target;
    }

    // Flushes here (rather than relying on the transaction's implicit flush at
    // commit) so a concurrent request that already committed a different
    // default trips the uq_payment_methods_one_default constraint inside this
    // try block, where it can be translated into a clean 409 instead of
    // surfacing as a raw constraint-violation 500 after the method returns.
    private PaymentMethod saveDefaultSafely(PaymentMethod paymentMethod) {
        try {
            return paymentMethodRepository.saveAndFlush(paymentMethod);
        } catch (DataIntegrityViolationException ex) {
            throw new PaymentMethodConflictException(paymentMethod.getUserId());
        }
    }

    @Transactional
    public void removePaymentMethod(UUID userId, UUID paymentMethodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(paymentMethodId));
        paymentMethodRepository.delete(paymentMethod);
        profileRepository.findById(userId)
                .filter(profile -> paymentMethodId.equals(profile.getDefaultPaymentId()))
                .ifPresent(profile -> profile.setDefaultPaymentId(null));
    }

    // Profile.defaultPaymentId is a denormalized pointer at the current default
    // payment method — keep it in sync any time the default changes here.
    private void syncProfileDefault(UUID userId, UUID paymentMethodId) {
        profileRepository.findById(userId).ifPresent(profile -> profile.setDefaultPaymentId(paymentMethodId));
    }

    private void clearExistingDefault(UUID userId) {
        paymentMethodRepository.findByUserId(userId).stream()
                .filter(PaymentMethod::isDefault)
                .forEach(pm -> pm.setDefault(false));
    }
}
