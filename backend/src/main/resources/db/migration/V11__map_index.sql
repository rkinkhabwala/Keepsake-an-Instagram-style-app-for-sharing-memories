-- Map queries filter by a latitude/longitude box; only memories with coordinates are ever on the map.
CREATE INDEX ix_memories_location ON memories (lat, lng) WHERE lat IS NOT NULL AND deleted_at IS NULL;
