CREATE TABLE memories (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    caption VARCHAR(2200),
    occurred_on DATE NOT NULL,
    location_name VARCHAR(200),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    visibility VARCHAR(16) NOT NULL,
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_memories_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'CLOSE_FRIENDS', 'ONLY_ME')),
    CONSTRAINT ck_memories_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_memories_coordinates CHECK (
        (lat IS NULL AND lng IS NULL)
        OR (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180)),
    CONSTRAINT ck_memories_counts CHECK (like_count >= 0 AND comment_count >= 0)
);

CREATE INDEX ix_memories_author_created ON memories (author_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_memories_author_occurred ON memories (author_id, occurred_on);

CREATE TABLE memory_media (
    id UUID PRIMARY KEY,
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    upload_id UUID NOT NULL REFERENCES media_uploads (id),
    position SMALLINT NOT NULL,
    type VARCHAR(8) NOT NULL,
    original_key TEXT NOT NULL,
    display_key TEXT,
    thumb_key TEXT,
    width INTEGER,
    height INTEGER,
    duration_ms INTEGER,
    blurhash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_memory_media_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT ck_memory_media_type CHECK (type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT uq_memory_media_position UNIQUE (memory_id, position),
    CONSTRAINT uq_memory_media_upload UNIQUE (upload_id)
);

CREATE TABLE memory_people (
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (memory_id, user_id)
);

CREATE INDEX ix_memory_people_user ON memory_people (user_id);
