-- V16 added ix_memories_author_id, but V6's ix_memories_author_id_desc already indexes (author_id, id DESC)
-- for undeleted memories.
DROP INDEX IF EXISTS ix_memories_author_id;
