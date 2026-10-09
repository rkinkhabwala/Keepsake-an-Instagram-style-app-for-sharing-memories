-- Counter flushes recompute like_count from the likes rows, which needs likes by memory.
CREATE INDEX ix_likes_memory ON likes (memory_id);
