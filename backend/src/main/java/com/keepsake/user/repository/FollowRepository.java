package com.keepsake.user.repository;

import com.keepsake.user.entity.Follow;
import com.keepsake.user.entity.FollowId;
import com.keepsake.user.entity.FollowStatus;
import com.keepsake.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    @Modifying
    @Query(value = """
            INSERT INTO follows (follower_id, followee_id, status, created_at, updated_at)
            VALUES (:followerId, :followeeId, :status, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(UUID followerId, UUID followeeId, String status);

    @Modifying
    @Query(value = """
            UPDATE follows SET status = 'ACCEPTED', updated_at = NOW()
            WHERE follower_id = :followerId AND followee_id = :followeeId AND status = 'PENDING'
            """, nativeQuery = true)
    int acceptPending(UUID followerId, UUID followeeId);

    /** Approves every pending request to the user, e.g. when their account becomes public. */
    @Modifying
    @Query(value = """
            UPDATE follows SET status = 'ACCEPTED', updated_at = NOW()
            WHERE followee_id = :followeeId AND status = 'PENDING'
            """, nativeQuery = true)
    int acceptAllPending(UUID followeeId);

    @Modifying
    @Query("""
            DELETE FROM Follow f
            WHERE f.id.followerId = :followerId AND f.id.followeeId = :followeeId AND f.status = :status
            """)
    int deleteWithStatus(UUID followerId, UUID followeeId, FollowStatus status);

    boolean existsByIdAndStatus(FollowId id, FollowStatus status);

    long countByIdFolloweeIdAndStatus(UUID followeeId, FollowStatus status);

    long countByIdFollowerIdAndStatus(UUID followerId, FollowStatus status);

    /** Users following {@code followeeId} with the given status, newest account first, ids below the cursor. */
    @Query("""
            SELECT u FROM Follow f, User u
            WHERE u.id = f.id.followerId AND f.id.followeeId = :followeeId AND f.status = :status
                AND u.id < :cursor
            ORDER BY u.id DESC
            """)
    List<User> findFollowers(UUID followeeId, FollowStatus status, UUID cursor, Limit limit);

    /** Users followed by {@code followerId} with the given status, newest account first, ids below the cursor. */
    @Query("""
            SELECT u FROM Follow f, User u
            WHERE u.id = f.id.followeeId AND f.id.followerId = :followerId AND f.status = :status
                AND u.id < :cursor
            ORDER BY u.id DESC
            """)
    List<User> findFollowees(UUID followerId, FollowStatus status, UUID cursor, Limit limit);
}
