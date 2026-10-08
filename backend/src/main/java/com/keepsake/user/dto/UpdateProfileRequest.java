package com.keepsake.user.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * PATCH body: a null field leaves the stored value unchanged; an empty bio clears it. {@code avatarUploadId} is an
 * upload from {@code POST /api/media/upload-urls}; the avatar changes once that image finishes processing.
 */
public record UpdateProfileRequest(
        @Size(max = 100)
        String displayName,
        @Size(max = 150)
        String bio,
        Boolean isPrivate,
        UUID avatarUploadId
) {
}
