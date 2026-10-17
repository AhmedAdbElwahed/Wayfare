package com.wayfare.service;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Placeholder until a payment provider is integrated (then delete this class); never logs the token itself. */
@Slf4j
@Component
class LoggingPaymentTokenRevoker implements PaymentTokenRevoker {

    @Override
    public void revoke(String providerToken) {
        log.warn("No payment provider integrated; a stored card token was not revoked remotely");
    }
}
