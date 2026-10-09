-- The avatar upload most recently requested; only that one may become the avatar when processing finishes.
ALTER TABLE users ADD COLUMN avatar_upload_id UUID;

-- Memory lists page by id (UUIDv7, so newest first), as the spec's cursor = last memory id describes.
CREATE INDEX ix_memories_author_id_desc ON memories (author_id, id DESC) WHERE deleted_at IS NULL;
