-- The feed merges followed celebrity accounts' memories in at read time: newest first by id, per author.
CREATE INDEX ix_memories_author_id ON memories (author_id, id DESC) WHERE deleted_at IS NULL;
