package com.keepsake.user.dto;

import com.keepsake.user.entity.User;
import java.util.UUID;

public record MeDto(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        boolean isPrivate
) {

    public static MeDto from(User user) {
        return new MeDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.isPrivate());
    }
}
