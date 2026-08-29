-- Enforces "at most one default payment method per rider" in the database
-- itself, not just in application code — see PaymentMethodService for why
-- the application-level check alone is racy under concurrent requests.
CREATE UNIQUE INDEX uq_payment_methods_one_default
    ON payment_methods (user_id)
    WHERE is_default;
