CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    photo_url VARCHAR(500),
    locale VARCHAR(10),
    default_payment_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
