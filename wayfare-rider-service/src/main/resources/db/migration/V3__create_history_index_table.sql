CREATE TABLE history_index (
    user_id UUID NOT NULL,
    trip_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, trip_id)
);
