CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(150) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0 AND capacity <= 1000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_rooms_name_case_insensitive ON rooms (LOWER(name));
