package com.keepsake.memory.entity;

import com.keepsake.common.id.UuidV7;
import com.keepsake.media.entity.MediaUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memory_media")
public class MemoryMedia {

    @Id
    private UUID id;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "upload_id", nullable = false, updatable = false)
    private UUID uploadId;

    @Column(nullable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MediaType type;

    @Column(name = "original_key", nullable = false)
    private String originalKey;

    @Column(name = "display_key")
    private String displayKey;

    @Column(name = "thumb_key")
    private String thumbKey;

    private Integer width;

    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(length = 64)
    private String blurhash;

    @Column(name = "poster_key")
    private String posterKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemoryMedia() {
    }

    public MemoryMedia(UUID memoryId, MediaUpload upload, int position) {
        this.id = UuidV7.generate();
        this.memoryId = memoryId;
        this.uploadId = upload.getId();
        this.position = (short) position;
        this.type = upload.isVideo() ? MediaType.VIDEO : MediaType.IMAGE;
        this.originalKey = upload.getOriginalKey();
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

    /** Copies the processed renditions from a READY upload. */
    public void applyRenditions(MediaUpload upload) {
        displayKey = upload.getDisplayKey();
        thumbKey = upload.getThumbKey();
        width = upload.getWidth();
        height = upload.getHeight();
        blurhash = upload.getBlurhash();
        posterKey = upload.getPosterKey();
        durationMs = upload.getDurationMs();
    }

    public boolean isReady() {
        return displayKey != null && thumbKey != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public int getPosition() {
        return position;
    }

    public MediaType getType() {
        return type;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public String getThumbKey() {
        return thumbKey;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getPosterKey() {
        return posterKey;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getBlurhash() {
        return blurhash;
    }
}
