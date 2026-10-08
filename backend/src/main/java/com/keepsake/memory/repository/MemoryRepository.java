package com.keepsake.memory.repository;

import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryAccessPolicy;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * List queries filter with {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL} and page by (created_at, id)
 * descending, strictly below the cursor memory.
 */
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memory m WHERE m.id = :id")
    Optional<Memory> findByIdForUpdate(UUID id);

    /** Home feed, fanned out on read: the viewer's own memories plus those of accounts they follow (ACCEPTED). */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE (m.author_id = :viewerId OR m.author_id IN (
                    SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId AND f.status = 'ACCEPTED'))
                AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findFeed(UUID viewerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    /** One author's memories that the viewer may see, for the profile grid. */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.author_id = :authorId
                AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findByAuthorVisibleTo(UUID authorId, UUID viewerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT COUNT(*) FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.author_id = :authorId AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL,
            nativeQuery = true)
    long countByAuthorVisibleTo(UUID authorId, UUID viewerId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM memories m JOIN users a ON a.id = m.author_id
                WHERE m.id = :memoryId AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + ")",
            nativeQuery = true)
    boolean isVisibleTo(UUID memoryId, UUID viewerId);
}
