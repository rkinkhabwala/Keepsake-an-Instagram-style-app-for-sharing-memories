package com.keepsake.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MemoryPersonId implements Serializable {

    @Column(name = "memory_id", nullable = false)
    private UUID memoryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected MemoryPersonId() {
    }

    public MemoryPersonId(UUID memoryId, UUID userId) {
        this.memoryId = memoryId;
        this.userId = userId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MemoryPersonId that
                && Objects.equals(memoryId, that.memoryId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memoryId, userId);
    }
}
