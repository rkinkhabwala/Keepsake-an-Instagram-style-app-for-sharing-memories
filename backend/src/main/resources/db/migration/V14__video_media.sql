-- Videos (Phase 3): the 720p MP4 is display_key, a 1080px WebP poster is poster_key, and thumb_key is the
-- 320px square thumb of that poster.
ALTER TABLE media_uploads ADD COLUMN poster_key TEXT;
ALTER TABLE media_uploads ADD COLUMN duration_ms INTEGER;
ALTER TABLE memory_media ADD COLUMN poster_key TEXT;
