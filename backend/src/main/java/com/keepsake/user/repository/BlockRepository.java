package com.keepsake.user.repository;

import com.keepsake.user.entity.Block;
import com.keepsake.user.entity.BlockId;
import com.keepsake.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, BlockId> {

    @Modifying
    @Query(value = """
            INSERT INTO blocks (blocker_id, blocked_id, created_at, updated_at)
            VALUES (:blockerId, :blockedId, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID blockerId, UUID blockedId);

    /** True when either user has blocked the other. */
    @Query("""
            SELECT COUNT(b) > 0 FROM Block b
            WHERE (b.id.blockerId = :first AND b.id.blockedId = :second)
               OR (b.id.blockerId = :second AND b.id.blockedId = :first)
            """)
    boolean existsBetween(UUID first, UUID second);

    /** The users {@code blockerId} has blocked, newest account first, ids below the cursor. */
    @Query("""
            SELECT u FROM Block b, User u
            WHERE u.id = b.id.blockedId AND b.id.blockerId = :blockerId AND u.id < :cursor
            ORDER BY u.id DESC
            """)
    List<User> findBlocked(UUID blockerId, UUID cursor, Limit limit);

    /** Removes follows and pending requests in both directions. */
    @Modifying
    @Query(value = """
            DELETE FROM follows
            WHERE (follower_id = :first AND followee_id = :second)
               OR (follower_id = :second AND followee_id = :first)
            """, nativeQuery = true)
    int deleteFollowsBetween(UUID first, UUID second);

    @Modifying
    @Query(value = """
            DELETE FROM close_friends
            WHERE (owner_id = :first AND friend_id = :second)
               OR (owner_id = :second AND friend_id = :first)
            """, nativeQuery = true)
    int deleteCloseFriendsBetween(UUID first, UUID second);

    /** Removes tags of either user on the other's memories. */
    @Modifying
    @Query(value = """
            DELETE FROM memory_people p USING memories m
            WHERE p.memory_id = m.id
              AND ((m.author_id = :first AND p.user_id = :second)
                OR (m.author_id = :second AND p.user_id = :first))
            """, nativeQuery = true)
    int deleteTagsBetween(UUID first, UUID second);
}
