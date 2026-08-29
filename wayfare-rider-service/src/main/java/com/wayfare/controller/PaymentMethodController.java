package com.wayfare.controller;

import com.wayfare.dto.AddPaymentMethodRequest;
import com.wayfare.dto.PaymentMethodResponse;
import com.wayfare.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/riders/me/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public List<PaymentMethodResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return paymentMethodService.list(UUID.fromString(jwt.getSubject())).stream()
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> add(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AddPaymentMethodRequest request) {
        PaymentMethodResponse response = PaymentMethodResponse.from(
                paymentMethodService.addPaymentMethod(UUID.fromString(jwt.getSubject()), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{paymentMethodId}/default")
    public ResponseEntity<PaymentMethodResponse> setDefault(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentMethodId) {
        PaymentMethodResponse response = PaymentMethodResponse.from(
                paymentMethodService.setDefault(UUID.fromString(jwt.getSubject()), paymentMethodId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{paymentMethodId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentMethodId) {
        paymentMethodService.removePaymentMethod(UUID.fromString(jwt.getSubject()), paymentMethodId);
        return ResponseEntity.noContent().build();
    }
}
