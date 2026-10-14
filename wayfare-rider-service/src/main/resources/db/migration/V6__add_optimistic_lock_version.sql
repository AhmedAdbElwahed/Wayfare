-- Optimistic locking (@Version): concurrent PATCH / payment-method writes now
-- fail with 409 instead of being last-write-wins. Existing rows start at 0.
ALTER TABLE profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_methods ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
