package com.wayfare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.PaymentMethod;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByUserId(UUID userId);

    Optional<PaymentMethod> findByIdAndUserId(UUID id, UUID userId);
}
