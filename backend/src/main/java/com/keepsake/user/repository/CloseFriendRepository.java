package com.keepsake.user.repository;

import com.keepsake.user.entity.CloseFriend;
import com.keepsake.user.entity.CloseFriendId;
import com.keepsake.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CloseFriendRepository extends JpaRepository<CloseFriend, CloseFriendId> {

    @Modifying
    @Query(value = """
            INSERT INTO close_friends (owner_id, friend_id, created_at, updated_at)
            VALUES (:ownerId, :friendId, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(UUID ownerId, UUID friendId);

    @Query("""
            SELECT u FROM CloseFriend c, User u
            WHERE u.id = c.id.friendId AND c.id.ownerId = :ownerId AND u.id < :cursor
            ORDER BY u.id DESC
            """)
    List<User> findFriends(UUID ownerId, UUID cursor, Limit limit);
}
