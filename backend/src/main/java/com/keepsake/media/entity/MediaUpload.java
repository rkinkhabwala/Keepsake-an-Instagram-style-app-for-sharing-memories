package com.keepsake.media.entity;

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
@Table(name = "media_uploads")
public class MediaUpload {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_key", nullable = false)
    private String originalKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaUploadStatus status;

    @Column(name = "display_key")
    private String displayKey;

    @Column(name = "thumb_key")
    private String thumbKey;

    private Integer width;

    private Integer height;

    @Column(length = 64)
    private String blurhash;

    @Column(name = "taken_on")
    private LocalDate takenOn;

    @Column(name = "gps_lat")
    private Double gpsLat;

    @Column(name = "gps_lng")
    private Double gpsLng;

    @Column(name = "poster_key")
    private String posterKey;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaUpload() {
    }

    public MediaUpload(UUID ownerId, String contentType, long sizeBytes) {
        this.id = UuidV7.generate();
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.originalKey = "originals/" + ownerId + "/" + id;
        this.status = MediaUploadStatus.PENDING;
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

    public void markProcessing() {
        status = MediaUploadStatus.PROCESSING;
        failureReason = null;
    }

    public void markReady(ProcessedMedia result) {
        status = MediaUploadStatus.READY;
        displayKey = result.displayKey();
        thumbKey = result.thumbKey();
        width = result.width();
        height = result.height();
        blurhash = result.blurhash();
        takenOn = result.takenOn();
        gpsLat = result.latitude();
        gpsLng = result.longitude();
        posterKey = result.posterKey();
        durationMs = result.durationMs();
        failureReason = null;
    }

    public void markFailed(String reason) {
        status = MediaUploadStatus.FAILED;
        failureReason = reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    public String displayKeyFor() {
        return "display/" + id + ".webp";
    }

    public String videoKeyFor() {
        return "video/" + id + ".mp4";
    }

    public boolean isVideo() {
        return contentType.startsWith("video/");
    }

    public String getPosterKey() {
        return posterKey;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String thumbKeyFor() {
        return "thumb/" + id + ".webp";
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getOriginalKey() {
        return originalKey;
    }

    public MediaUploadStatus getStatus() {
        return status;
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

    public String getBlurhash() {
        return blurhash;
    }

    public LocalDate getTakenOn() {
        return takenOn;
    }

    public Double getGpsLat() {
        return gpsLat;
    }

    public Double getGpsLng() {
        return gpsLng;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
