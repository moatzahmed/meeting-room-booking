CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_bookings_time_range CHECK (start_time < end_time),
    CONSTRAINT chk_bookings_max_duration CHECK (end_time <= start_time + INTERVAL '4 hours')
);

CREATE INDEX idx_bookings_room_time
    ON bookings (room_id, start_time, end_time)
    WHERE status = 'CONFIRMED';

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT ex_bookings_no_confirmed_overlap
    EXCLUDE USING GIST (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    )
    WHERE (status = 'CONFIRMED');
