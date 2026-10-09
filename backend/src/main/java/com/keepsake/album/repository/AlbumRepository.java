package com.keepsake.album.repository;

import com.keepsake.album.entity.Album;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryAccessPolicy;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Albums, plus their memberships (album_memories) and collaborators, which are plain rows. */
public interface AlbumRepository extends JpaRepository<Album, UUID> {

    interface CollaboratorRow {
        UUID getUserId();

        String getRole();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Album a WHERE a.id = :id")
    Optional<Album> findByIdForUpdate(UUID id);

    @Query(value = "SELECT role FROM album_collaborators WHERE album_id = :albumId AND user_id = :userId",
            nativeQuery = true)
    Optional<String> findRole(UUID albumId, UUID userId);

    @Query(value = """
            SELECT user_id AS userId, role FROM album_collaborators WHERE album_id = :albumId ORDER BY created_at
            """, nativeQuery = true)
    List<CollaboratorRow> findCollaborators(UUID albumId);

    @Modifying
    @Query(value = """
            INSERT INTO album_collaborators (album_id, user_id, role, created_at, updated_at)
            VALUES (:albumId, :userId, :role, NOW(), NOW())
            ON CONFLICT (album_id, user_id) DO UPDATE SET role = EXCLUDED.role, updated_at = NOW()
            """, nativeQuery = true)
    void upsertCollaborator(UUID albumId, UUID userId, String role);

    @Modifying
    @Query(value = "DELETE FROM album_collaborators WHERE album_id = :albumId AND user_id = :userId",
            nativeQuery = true)
    int deleteCollaborator(UUID albumId, UUID userId);

    /** When someone stops collaborating, the memories they added leave with them. */
    @Modifying
    @Query(value = "DELETE FROM album_memories WHERE album_id = :albumId AND added_by = :userId", nativeQuery = true)
    int deleteMemoriesAddedBy(UUID albumId, UUID userId);

    @Query(value = "SELECT COALESCE(MAX(position), -1) FROM album_memories WHERE album_id = :albumId",
            nativeQuery = true)
    int maxPosition(UUID albumId);

    @Modifying
    @Query(value = """
            INSERT INTO album_memories (album_id, memory_id, position, added_by, created_at, updated_at)
            VALUES (:albumId, :memoryId, :position, :addedBy, NOW(), NOW())
            ON CONFLICT (album_id, memory_id) DO NOTHING
            """, nativeQuery = true)
    int insertMemory(UUID albumId, UUID memoryId, int position, UUID addedBy);

    @Query(value = "SELECT added_by FROM album_memories WHERE album_id = :albumId AND memory_id = :memoryId",
            nativeQuery = true)
    Optional<UUID> findAddedBy(UUID albumId, UUID memoryId);

    @Query(value = "SELECT position FROM album_memories WHERE album_id = :albumId AND memory_id = :memoryId",
            nativeQuery = true)
    Optional<Integer> findPosition(UUID albumId, UUID memoryId);

    @Modifying
    @Query(value = "DELETE FROM album_memories WHERE album_id = :albumId AND memory_id = :memoryId",
            nativeQuery = true)
    int deleteMemory(UUID albumId, UUID memoryId);

    /**
     * The album's memories that the viewer could see on their own (spec §12 Phase 2), in album order after the
     * given position. Being able to see the album grants nothing extra.
     */
    @Query(value = """
            SELECT m.* FROM album_memories am
            JOIN memories m ON m.id = am.memory_id
            JOIN users a ON a.id = m.author_id
            WHERE am.album_id = :albumId AND am.position > :afterPosition
              AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY am.position
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findVisibleMemories(UUID albumId, UUID viewerId, int afterPosition, int limit);

    @Query(value = """
            SELECT COUNT(*) FROM album_memories am
            JOIN memories m ON m.id = am.memory_id
            JOIN users a ON a.id = m.author_id
            WHERE am.album_id = :albumId AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL,
            nativeQuery = true)
    long countVisibleMemories(UUID albumId, UUID viewerId);

    /** Albums the user owns or collaborates on, newest first. */
    @Query(value = """
            SELECT al.* FROM albums al
            WHERE al.owner_id = :userId
               OR EXISTS (SELECT 1 FROM album_collaborators c WHERE c.album_id = al.id AND c.user_id = :userId)
            ORDER BY al.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Album> findOwnedOrCollaborating(UUID userId, int limit);
}
