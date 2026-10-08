package com.keepsake.user.dto;

import java.util.UUID;

public record ProfileDto(
        UUID id,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        boolean isPrivate,
        long memoryCount,
        long followerCount,
        long followingCount,
        Relationship relationship
) {
}
