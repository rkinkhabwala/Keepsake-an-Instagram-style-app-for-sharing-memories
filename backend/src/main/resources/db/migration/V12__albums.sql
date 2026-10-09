CREATE TABLE albums (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    cover_memory_id UUID REFERENCES memories (id) ON DELETE SET NULL,
    visibility VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_albums_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'CLOSE_FRIENDS', 'ONLY_ME')),
    CONSTRAINT ck_albums_title CHECK (char_length(title) BETWEEN 1 AND 100)
);

CREATE INDEX ix_albums_owner ON albums (owner_id);

CREATE TABLE album_memories (
    album_id UUID NOT NULL REFERENCES albums (id) ON DELETE CASCADE,
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    added_by UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (album_id, memory_id),
    CONSTRAINT uq_album_memories_position UNIQUE (album_id, position)
);

CREATE INDEX ix_album_memories_memory ON album_memories (memory_id);
CREATE INDEX ix_album_memories_added_by ON album_memories (added_by);

CREATE TABLE album_collaborators (
    album_id UUID NOT NULL REFERENCES albums (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (album_id, user_id),
    CONSTRAINT ck_album_collaborators_role CHECK (role IN ('EDITOR', 'VIEWER'))
);

CREATE INDEX ix_album_collaborators_user ON album_collaborators (user_id);
