package com.keepsake.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CloseFriendId implements Serializable {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "friend_id", nullable = false)
    private UUID friendId;

    protected CloseFriendId() {
    }

    public CloseFriendId(UUID ownerId, UUID friendId) {
        this.ownerId = ownerId;
        this.friendId = friendId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getFriendId() {
        return friendId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CloseFriendId that
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(friendId, that.friendId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, friendId);
    }
}
