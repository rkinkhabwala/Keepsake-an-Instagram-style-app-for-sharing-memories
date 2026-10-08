package com.keepsake.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AuthUserDto user
) {
}
