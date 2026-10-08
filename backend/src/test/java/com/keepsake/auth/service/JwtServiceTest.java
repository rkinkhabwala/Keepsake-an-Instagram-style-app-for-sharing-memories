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

        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart) + (first == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);
        assertThrows(InvalidAccessTokenException.class, () -> jwtService.verifyAccessToken(tampered));

        JwtService expiredVerifier = new JwtService(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-01-01T00:15:00Z"), ZoneOffset.UTC), SECRET);
        assertThrows(InvalidAccessTokenException.class, () -> expiredVerifier.verifyAccessToken(token));
    }

    @Test
    void rejectsNonCanonicalSignatureEncodingThatDecodesToTheSameBytes() {
        JwtService jwtService = new JwtService(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), SECRET);
        String token = jwtService.createAccessToken(UUID.randomUUID());
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        // A 32-byte signature leaves 2 unused bits in its last character; setting one changes the text, not the bytes.
        int last = alphabet.indexOf(token.charAt(token.length() - 1));
        String sameBytes = token.substring(0, token.length() - 1) + alphabet.charAt(last ^ 1);

        assertEquals(java.util.Arrays.toString(java.util.Base64.getUrlDecoder().decode(signature(token))),
                java.util.Arrays.toString(java.util.Base64.getUrlDecoder().decode(signature(sameBytes))));
        assertThrows(InvalidAccessTokenException.class, () -> jwtService.verifyAccessToken(sameBytes));
    }

    private static String signature(String token) {
        return token.substring(token.lastIndexOf('.') + 1);
    }
}
