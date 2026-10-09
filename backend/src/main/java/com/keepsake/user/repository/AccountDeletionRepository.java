package com.keepsake.user.repository;

import com.keepsake.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Bulk statements for deleting an account. Rows that reference the user go with it by ON DELETE CASCADE. */
public interface AccountDeletionRepository extends Repository<User, UUID> {

    /** Removes the user's likes from the counts on other people's memories. */
    @Modifying
    @Query(value = """
            UPDATE memories m SET like_count = GREATEST(m.like_count - liked.n, 0)
            FROM (SELECT memory_id, COUNT(*) AS n FROM likes WHERE user_id = :userId GROUP BY memory_id) liked
            WHERE m.id = liked.memory_id
            """, nativeQuery = true)
    void subtractLikes(UUID userId);

    /**
     * Removes from comment counts every live comment that disappears with the user: their own comments and the
     * replies to them (which cascade with their parent).
     */
    @Modifying
    @Query(value = """
            UPDATE memories m SET comment_count = GREATEST(m.comment_count - gone.n, 0)
            FROM (
                SELECT c.memory_id, COUNT(*) AS n FROM comments c
                WHERE c.deleted_at IS NULL
                    AND (c.author_id = :userId
                        OR c.parent_id IN (SELECT p.id FROM comments p WHERE p.author_id = :userId))
                GROUP BY c.memory_id) gone
            WHERE m.id = gone.memory_id
            """, nativeQuery = true)
    void subtractComments(UUID userId);

    /**
     * Deletes the user's memories (cascading to their media, tags, likes, comments, and saves) before the user:
     * memory_media must be gone before the uploads it references, which the user row also cascades to.
     */
    @Modifying
    @Query(value = "DELETE FROM memories WHERE author_id = :userId", nativeQuery = true)
    void deleteMemories(UUID userId);

    /** Cascades to tokens, follows, close friends, uploads, memories (and their media, tags, likes, comments, saves). */
    @Modifying
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    int deleteUser(UUID userId);
}
