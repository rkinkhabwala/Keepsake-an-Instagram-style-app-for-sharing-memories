package com.keepsake.user.dto;

import java.util.UUID;

public record MeDto(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        boolean isPrivate,
        String avatarUrl
) {
}
