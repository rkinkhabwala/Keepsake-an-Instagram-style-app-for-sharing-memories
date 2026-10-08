package com.keepsake.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hmac";

    @Test
    void signsAndVerifiesAccessTokenForSubject() {
        JwtService jwtService = new JwtService(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), SECRET);
        UUID userId = UUID.randomUUID();

        String token = jwtService.createAccessToken(userId);

        assertEquals(userId, jwtService.verifyAccessToken(token));
    }

    @Test
    void rejectsTamperedAndExpiredTokens() {
        JwtService jwtService = new JwtService(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), SECRET);
        String token = jwtService.createAccessToken(UUID.randomUUID());

        assertThrows(InvalidAccessTokenException.class,
                () -> jwtService.verifyAccessToken(token.substring(0, token.length() - 1) + "x"));

        JwtService expiredVerifier = new JwtService(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-01-01T00:15:00Z"), ZoneOffset.UTC), SECRET);
        assertThrows(InvalidAccessTokenException.class, () -> expiredVerifier.verifyAccessToken(token));
    }
}
