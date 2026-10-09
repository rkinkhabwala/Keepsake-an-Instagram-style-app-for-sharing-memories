package com.keepsake.user.entity;

import com.keepsake.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 150)
    private String bio;

    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "avatar_upload_id")
    private UUID avatarUploadId;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(
            String username,
            String email,
            String passwordHash,
            String displayName
    ) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UuidV7.generate();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void requestAvatar(UUID uploadId) {
        this.avatarUploadId = uploadId;
    }

    public UUID getAvatarUploadId() {
        return avatarUploadId;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public void updateProfile(String displayName, String bio, boolean isPrivate) {
        this.displayName = displayName;
        this.bio = bio;
        this.isPrivate = isPrivate;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public boolean isPrivate() {
        return isPrivate;
    }
}
