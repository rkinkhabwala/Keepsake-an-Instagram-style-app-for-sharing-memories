package com.keepsake.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A person tagged as having been there. */
@Entity
@Table(name = "memory_people")
public class MemoryPerson {

    @EmbeddedId
    private MemoryPersonId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemoryPerson() {
    }

    public MemoryPerson(UUID memoryId, UUID userId) {
        this.id = new MemoryPersonId(memoryId, userId);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public MemoryPersonId getId() {
        return id;
    }
}
