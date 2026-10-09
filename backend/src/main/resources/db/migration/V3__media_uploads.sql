CREATE TABLE media_uploads (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    original_key TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    display_key TEXT,
    thumb_key TEXT,
    width INTEGER,
    height INTEGER,
    blurhash VARCHAR(64),
    taken_on DATE,
    gps_lat DOUBLE PRECISION,
    gps_lng DOUBLE PRECISION,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_media_uploads_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_media_uploads_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_media_uploads_owner ON media_uploads (owner_id);
