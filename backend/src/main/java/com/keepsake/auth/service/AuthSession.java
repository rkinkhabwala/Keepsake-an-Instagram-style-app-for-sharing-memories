package com.keepsake.auth.service;

import com.keepsake.auth.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
