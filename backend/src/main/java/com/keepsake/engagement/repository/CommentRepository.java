package com.keepsake.engagement.repository;

import com.keepsake.engagement.entity.Comment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Top-level comments oldest first, skipping deleted ones unless they still have live replies. */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.memoryId = :memoryId AND c.parentId IS NULL AND c.id > :cursor
                AND (c.deletedAt IS NULL OR EXISTS (
                    SELECT 1 FROM Comment r WHERE r.parentId = c.id AND r.deletedAt IS NULL))
            ORDER BY c.id ASC
            """)
    List<Comment> findTopLevel(UUID memoryId, UUID cursor, Limit limit);

    /** @return 1 if this call deleted the comment, 0 if it was already deleted */
    @Modifying
    @Query(value = "UPDATE comments SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    int softDelete(UUID id, Instant now);

    @Query("SELECT c FROM Comment c WHERE c.parentId IN :parentIds AND c.deletedAt IS NULL ORDER BY c.id ASC")
    List<Comment> findLiveReplies(Collection<UUID> parentIds);

    /** The latest two live top-level comments of each memory, oldest first, for feed cards. */
    @Query(value = """
            SELECT c.* FROM comments c WHERE c.id IN (
                SELECT ranked.id FROM (
                    SELECT p.id, ROW_NUMBER() OVER (
                        PARTITION BY p.memory_id ORDER BY p.created_at DESC, p.id DESC) AS rank
                    FROM comments p
                    WHERE p.memory_id IN (:memoryIds) AND p.parent_id IS NULL AND p.deleted_at IS NULL
                ) ranked WHERE ranked.rank <= 2)
            ORDER BY c.created_at ASC, c.id ASC
            """, nativeQuery = true)
    List<Comment> findPreviewComments(Collection<UUID> memoryIds);
}
