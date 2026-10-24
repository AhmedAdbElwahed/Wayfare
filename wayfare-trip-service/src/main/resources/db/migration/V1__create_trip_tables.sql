-- Write side of the trip service: current state (trips), the append-only
-- history (trip_events) and the transactional outbox (outbox).

CREATE TABLE trips (
    id             UUID PRIMARY KEY,
    rider_id       UUID           NOT NULL,
    driver_id      UUID,
    status         VARCHAR(32)    NOT NULL,
    pickup_lat     DOUBLE PRECISION NOT NULL,
    pickup_lng     DOUBLE PRECISION NOT NULL,
    dropoff_lat    DOUBLE PRECISION NOT NULL,
    dropoff_lng    DOUBLE PRECISION NOT NULL,
    fare_locked    NUMERIC(12, 2),
    fare_final     NUMERIC(12, 2),
    requested_at   TIMESTAMPTZ,
    started_at     TIMESTAMPTZ,
    ended_at       TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_trips_rider ON trips (rider_id);

CREATE TABLE trip_events (
    id           UUID PRIMARY KEY,
    trip_id      UUID         NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_trip_events_trip ON trip_events (trip_id, occurred_at);

CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    -- Serialized protobuf TripEvent. Bytes, not JSON: this is the wire format
    -- the relay hands to the Schema Registry serializer (trip_events keeps the
    -- readable JSON copy).
    payload       BYTEA        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ            -- NULL = not yet published
);
-- The relay only ever asks for unpublished rows, oldest first; a partial
-- index keeps that query cheap however large the published history grows.
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
