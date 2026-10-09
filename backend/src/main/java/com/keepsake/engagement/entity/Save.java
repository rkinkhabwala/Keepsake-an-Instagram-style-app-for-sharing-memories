package com.keepsake.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "saves")
public class Save {

    @EmbeddedId
    private UserMemoryId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Save() {
    }

    public UserMemoryId getId() {
        return id;
    }
}
