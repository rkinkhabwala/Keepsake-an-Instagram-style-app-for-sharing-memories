package com.keepsake.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "close_friends")
public class CloseFriend {

    @EmbeddedId
    private CloseFriendId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CloseFriend() {
    }

    public CloseFriendId getId() {
        return id;
    }
}
