CREATE TABLE hashtags (
    id UUID PRIMARY KEY,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_hashtags_tag UNIQUE (tag)
);

CREATE TABLE memory_hashtags (
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    hashtag_id UUID NOT NULL REFERENCES hashtags (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (memory_id, hashtag_id)
);

-- Hashtag pages list a tag's memories newest first.
CREATE INDEX ix_memory_hashtags_hashtag ON memory_hashtags (hashtag_id, memory_id DESC);
