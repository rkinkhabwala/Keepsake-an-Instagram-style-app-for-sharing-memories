package com.keepsake.auth.dto;

import com.keepsake.user.entity.User;
import java.util.UUID;

public record AuthUserDto(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        boolean isPrivate
) {

    public static AuthUserDto from(User user) {
        return new AuthUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.isPrivate());
    }
}
