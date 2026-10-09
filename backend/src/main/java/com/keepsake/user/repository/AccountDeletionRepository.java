package com.keepsake.user.repository;

import com.keepsake.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Bulk statements for deleting an account. Rows that reference the user go with it by ON DELETE CASCADE. */
public interface AccountDeletionRepository extends Repository<User, UUID> {

    /**
     * Other people's memories whose counts change when the user goes: ones they liked, commented on, or that hold
     * replies to their comments (which cascade with their parent).
     */
    @Query(value = """
            SELECT l.memory_id FROM likes l WHERE l.user_id = :userId
            UNION
            SELECT c.memory_id FROM comments c
            WHERE c.author_id = :userId OR c.parent_id IN (SELECT p.id FROM comments p WHERE p.author_id = :userId)
            """, nativeQuery = true)
    java.util.List<UUID> findMemoriesWithEngagementBy(UUID userId);

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
