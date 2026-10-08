CREATE TABLE likes (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, memory_id)
);

CREATE TABLE comments (
    id UUID PRIMARY KEY,
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id UUID REFERENCES comments (id) ON DELETE CASCADE,
    body VARCHAR(500) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_comments_body CHECK (char_length(body) BETWEEN 1 AND 500)
);

CREATE INDEX ix_comments_memory_created ON comments (memory_id, created_at);
CREATE INDEX ix_comments_parent ON comments (parent_id) WHERE parent_id IS NOT NULL;

-- Replies are one level deep: a reply's parent must be a top-level comment on the same memory.
CREATE FUNCTION comments_enforce_one_level() RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM comments p
        WHERE p.id = NEW.parent_id AND p.parent_id IS NULL AND p.memory_id = NEW.memory_id
    ) THEN
        RAISE EXCEPTION 'A reply must target a top-level comment on the same memory'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_comments_one_level
    BEFORE INSERT OR UPDATE OF parent_id, memory_id ON comments
    FOR EACH ROW EXECUTE FUNCTION comments_enforce_one_level();

CREATE TABLE saves (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    memory_id UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, memory_id)
);

CREATE INDEX ix_saves_user_created ON saves (user_id, created_at DESC);
