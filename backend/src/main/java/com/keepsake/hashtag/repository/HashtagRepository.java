package com.keepsake.hashtag.repository;

import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryAccessPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Hashtags are plain lookup rows, written with native statements; no entity is needed. */
public interface HashtagRepository extends Repository<Memory, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO hashtags (id, tag, created_at, updated_at)
            SELECT CAST(:id AS uuid), :tag, NOW(), NOW()
            ON CONFLICT (tag) DO NOTHING
            """, nativeQuery = true)
    void insertTagIfAbsent(UUID id, String tag);

    @Query(value = "SELECT id FROM hashtags WHERE tag = :tag", nativeQuery = true)
    Optional<UUID> findIdByTag(String tag);

    @Modifying
    @Query(value = "DELETE FROM memory_hashtags WHERE memory_id = :memoryId", nativeQuery = true)
    void deleteForMemory(UUID memoryId);

    @Modifying
    @Query(value = """
            INSERT INTO memory_hashtags (memory_id, hashtag_id, created_at, updated_at)
            SELECT :memoryId, h.id, NOW(), NOW() FROM hashtags h WHERE h.tag IN (:tags)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void linkTags(UUID memoryId, Collection<String> tags);

    /** A tag's memories that the viewer may see, newest first, below the cursor id. */
    @Query(value = """
            SELECT m.* FROM memory_hashtags mh
            JOIN memories m ON m.id = mh.memory_id
            JOIN users a ON a.id = m.author_id
            WHERE mh.hashtag_id = :hashtagId AND m.id < :cursor
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findMemoriesVisibleTo(UUID hashtagId, UUID viewerId, UUID cursor, int limit);
}
