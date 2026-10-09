package com.keepsake.memory.entity;

import com.keepsake.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "memories")
public class Memory {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(length = 2200)
    private String caption;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "location_name", length = 200)
    private String locationName;

    private Double lat;

    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemoryStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Memory() {
    }

    public Memory(UUID authorId, LocalDate occurredOn, Visibility visibility) {
        this.id = UuidV7.generate();
        this.authorId = authorId;
        this.occurredOn = occurredOn;
        this.visibility = visibility;
        this.status = MemoryStatus.PROCESSING;
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

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public void setLocation(String name, Double latitude, Double longitude) {
        this.locationName = name;
        this.lat = latitude;
        this.lng = longitude;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void setStatus(MemoryStatus status) {
        this.status = status;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getCaption() {
        return caption;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getLocationName() {
        return locationName;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
