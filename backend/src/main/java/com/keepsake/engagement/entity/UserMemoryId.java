package com.keepsake.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Key of a (user, memory) pair, shared by likes and saves. */
@Embeddable
public class UserMemoryId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "memory_id", nullable = false)
    private UUID memoryId;

    protected UserMemoryId() {
    }

    public UserMemoryId(UUID userId, UUID memoryId) {
        this.userId = userId;
        this.memoryId = memoryId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserMemoryId that
                && Objects.equals(userId, that.userId)
                && Objects.equals(memoryId, that.memoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, memoryId);
    }
}
