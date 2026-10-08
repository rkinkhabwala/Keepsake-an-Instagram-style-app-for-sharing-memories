package com.keepsake.user.dto;

import java.util.UUID;

public record ProfileDto(
        UUID id,
        String username,
        String displayName,
        String bio,
        boolean isPrivate,
        long followerCount,
        long followingCount,
        Relationship relationship
) {
}
