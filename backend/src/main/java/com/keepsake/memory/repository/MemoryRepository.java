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
import org.springframework.data.jpa.repository.Modifying;
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

    /** Memories the user was tagged in that the viewer may see, newest first. */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            JOIN memory_people p ON p.memory_id = m.id AND p.user_id = :taggedUserId
            WHERE (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findTaggedVisibleTo(
            UUID taggedUserId, UUID viewerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    /**
     * The viewer's saved memories, most recently saved first, that they may still see. Paged by the save's time,
     * so {@code cursorCreatedAt} is the cursor memory's save time.
     */
    @Query(value = """
            SELECT m.* FROM saves s JOIN memories m ON m.id = s.memory_id JOIN users a ON a.id = m.author_id
            WHERE s.user_id = :viewerId
                AND (s.created_at, s.memory_id) < (:cursorCreatedAt, :cursorId)
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY s.created_at DESC, s.memory_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findSavedVisibleTo(UUID viewerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    @Query(value = "SELECT created_at FROM saves WHERE user_id = :userId AND memory_id = :memoryId",
            nativeQuery = true)
    Optional<Instant> findSaveTime(UUID userId, UUID memoryId);

    @Modifying
    @Query(value = "UPDATE memories SET like_count = GREATEST(like_count + :delta, 0) WHERE id = :memoryId",
            nativeQuery = true)
    void adjustLikeCount(UUID memoryId, int delta);

    @Modifying
    @Query(value = "UPDATE memories SET comment_count = GREATEST(comment_count + :delta, 0) WHERE id = :memoryId",
            nativeQuery = true)
    void adjustCommentCount(UUID memoryId, int delta);

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
