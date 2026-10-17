package com.wayfare.service;

/**
 * Tells the payment provider to forget a stored card token. Erasing our row
 * alone would leave the token live at the provider. There is no provider
 * integration yet, so the only implementation just logs; replace it when
 * one exists. Must be idempotent and throw on failure (the erasure is then
 * retried, and the token is still in our database to retry with).
 */
public interface PaymentTokenRevoker {

    void revoke(String providerToken);
}
