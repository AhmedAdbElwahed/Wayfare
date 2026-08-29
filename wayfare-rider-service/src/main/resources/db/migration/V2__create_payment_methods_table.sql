CREATE TABLE payment_methods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider_token VARCHAR(255) NOT NULL,
    brand VARCHAR(50),
    last4 VARCHAR(4),
    is_default BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_payment_methods_user_id ON payment_methods (user_id);
