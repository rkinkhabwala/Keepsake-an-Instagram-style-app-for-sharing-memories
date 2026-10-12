package com.keepsake.memory.repository;

import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryAccessPolicy;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * List queries filter with {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL}. Memory lists page by id descending
 * (UUIDv7, so newest first) strictly below the cursor id, which is never looked up, so a cursor cannot be used
 * to learn whether a hidden memory exists.
 */
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memory m WHERE m.id = :id")
    Optional<Memory> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memory m WHERE m.status = com.keepsake.memory.entity.MemoryStatus.PROCESSING"
            + " AND m.updatedAt < :before")
    List<Memory> findStaleProcessing(Instant before);

    /** Home feed, fanned out on read: the viewer's own memories plus those of accounts they follow (ACCEPTED). */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE (m.author_id = :viewerId OR m.author_id IN (
                    SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId AND f.status = 'ACCEPTED'))
                AND m.id < :cursor
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findFeed(UUID viewerId, UUID cursor, int limit);

    /** The newest ids of {@link #findFeed}, to rebuild a feed in Redis. */
    @Query(value = """
            SELECT m.id FROM memories m JOIN users a ON a.id = m.author_id
            WHERE (m.author_id = :viewerId OR m.author_id IN (
                    SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId AND f.status = 'ACCEPTED'))
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findFeedIds(UUID viewerId, int limit);

    /** Of these fanned-out ids, the memories that still belong in the viewer's feed (same scope as findFeed). */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.id IN (:ids)
                AND (m.author_id = :viewerId OR EXISTS (
                    SELECT 1 FROM follows f WHERE f.follower_id = :viewerId AND f.followee_id = m.author_id
                        AND f.status = 'ACCEPTED'))
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL, nativeQuery = true)
    List<Memory> findFeedCandidatesVisibleTo(UUID viewerId, Collection<UUID> ids);

    /** {@link #findFeed} limited to these authors: the followed celebrity accounts merged in at read time. */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.author_id IN (:authorIds)
                AND EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :viewerId
                    AND f.followee_id = m.author_id AND f.status = 'ACCEPTED')
                AND m.id < :cursor
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findFeedFromAuthors(UUID viewerId, Collection<UUID> authorIds, UUID cursor, int limit);

    /** One author's memories that the viewer may see, for the profile grid. */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.author_id = :authorId
                AND m.id < :cursor
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findByAuthorVisibleTo(UUID authorId, UUID viewerId, UUID cursor, int limit);

    /** Memories the user was tagged in that the viewer may see, newest first. */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            JOIN memory_people p ON p.memory_id = m.id AND p.user_id = :taggedUserId
            WHERE m.id < :cursor
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findTaggedVisibleTo(
            UUID taggedUserId, UUID viewerId, UUID cursor, int limit);

    /**
     * The viewer's saved memories, most recently saved first, that they may still see. Paged by (save time,
     * memory id), carried in an opaque cursor so it keeps working after the cursor memory is unsaved.
     */
    @Query(value = """
            SELECT m.* FROM saves s JOIN memories m ON m.id = s.memory_id JOIN users a ON a.id = m.author_id
            WHERE s.user_id = :viewerId
                AND (s.created_at, s.memory_id) < (:savedBefore, :cursorId)
                AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY s.created_at DESC, s.memory_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findSavedVisibleTo(UUID viewerId, Instant savedBefore, UUID cursorId, int limit);

    @Query(value = "SELECT created_at FROM saves WHERE user_id = :userId AND memory_id = :memoryId",
            nativeQuery = true)
    Optional<Instant> findSaveTime(UUID userId, UUID memoryId);

    /**
     * Memories with coordinates inside a box, for the map: the viewer's own, those of accounts they follow
     * (ACCEPTED), and those they are tagged in, all still filtered by the visibility rules. When
     * {@code wrapsAntimeridian} the longitude range is {@code lng >= minLng OR lng <= maxLng}.
     */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE m.lat IS NOT NULL AND m.deleted_at IS NULL
              AND m.lat BETWEEN :minLat AND :maxLat
              AND ((NOT :wrapsAntimeridian AND m.lng BETWEEN :minLng AND :maxLng)
                OR (:wrapsAntimeridian AND (m.lng >= :minLng OR m.lng <= :maxLng)))
              AND (m.author_id = :viewerId
                OR m.author_id IN (SELECT f.followee_id FROM follows f
                                   WHERE f.follower_id = :viewerId AND f.status = 'ACCEPTED')
                OR EXISTS (SELECT 1 FROM memory_people p WHERE p.memory_id = m.id AND p.user_id = :viewerId))
              AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findOnMap(
            UUID viewerId,
            double minLng,
            double minLat,
            double maxLng,
            double maxLat,
            boolean wrapsAntimeridian,
            int limit);

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
