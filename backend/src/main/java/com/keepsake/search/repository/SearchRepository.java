package com.keepsake.search.repository;

import com.keepsake.memory.service.MemoryAccessPolicy;
import com.keepsake.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Prefix lookups served by the text_pattern_ops indexes in V10; prefixes arrive already LIKE-escaped. */
public interface SearchRepository extends Repository<User, UUID> {

    /** Users whose username or display name starts with the prefix, minus anyone in a block with the viewer. */
    @Query(value = """
            SELECT u.* FROM users u
            WHERE (lower(u.username) LIKE :prefix ESCAPE '\\' OR lower(u.display_name) LIKE :prefix ESCAPE '\\')
              AND NOT EXISTS (SELECT 1 FROM blocks b
                  WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                     OR (b.blocker_id = u.id AND b.blocked_id = :viewerId))
            ORDER BY (lower(u.username) = :exact) DESC, u.username
            LIMIT :limit
            """, nativeQuery = true)
    List<User> findUsers(String prefix, String exact, UUID viewerId, int limit);

    /**
     * Tags starting with the prefix that are on at least one memory the viewer may see, so tags used only in
     * hidden memories are not revealed.
     */
    @Query(value = """
            SELECT h.tag FROM hashtags h
            WHERE h.tag LIKE :prefix ESCAPE '\\'
              AND EXISTS (
                  SELECT 1 FROM memory_hashtags mh
                  JOIN memories m ON m.id = mh.memory_id
                  JOIN users a ON a.id = m.author_id
                  WHERE mh.hashtag_id = h.id AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
              )
            ORDER BY h.tag
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findHashtags(String prefix, UUID viewerId, int limit);
}
