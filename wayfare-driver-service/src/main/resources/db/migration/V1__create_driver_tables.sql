CREATE TABLE drivers (
    id UUID PRIMARY KEY,                 -- = Account.id from auth-service (JWT sub)
    name VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    rating_avg NUMERIC(3, 2),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_drivers_phone ON drivers (phone) WHERE phone IS NOT NULL;

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL UNIQUE REFERENCES drivers (id) ON DELETE CASCADE,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    plate VARCHAR(20) NOT NULL,
    color VARCHAR(50),
    capacity INTEGER NOT NULL,
    type VARCHAR(30) NOT NULL
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    url VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_documents_driver_id ON documents (driver_id);
CREATE INDEX idx_documents_expiry ON documents (expires_at) WHERE status = 'VALID';
