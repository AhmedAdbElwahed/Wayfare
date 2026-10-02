package com.wayfare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wayfare.domain.PaymentMethod;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByUserId(UUID userId);

    Optional<PaymentMethod> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Executes immediately, unlike dirty-checked entity updates: Hibernate
     * flushes inserts before updates, so clearing the old default via entity
     * state would let the new default's INSERT hit uq_payment_methods_one_default
     * first and 409 every legitimate default change.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentMethod p set p.isDefault = false where p.userId = :userId and p.isDefault = true")
    void clearDefault(@Param("userId") UUID userId);
}
