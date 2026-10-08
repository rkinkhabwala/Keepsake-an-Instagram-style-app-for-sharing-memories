package com.keepsake.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final String ISSUER = "keepsake";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecretKeySpec secretKey;

    public JwtService(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${keepsake.jwt-secret}") String secret
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secretKey = new SecretKeySpec(secretBytes, ALGORITHM);
    }

    public String createAccessToken(UUID userId) {
        Instant issuedAt = clock.instant();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "iss", ISSUER,
                "sub", userId.toString(),
                "iat", issuedAt.getEpochSecond(),
                "exp", issuedAt.plus(ACCESS_TOKEN_LIFETIME).getEpochSecond());
        String signingInput = encodeJson(header) + "." + encodeJson(payload);
        return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    }

    public UUID verifyAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new InvalidAccessTokenException();
            }

            String signingInput = parts[0] + "." + parts[1];
            // Compare the canonical encoding, not decoded bytes: the decoder ignores unused trailing bits, so
            // several spellings of the last character would otherwise verify as the same signature.
            byte[] expectedSignature = ENCODER.encodeToString(sign(signingInput)).getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expectedSignature, parts[2].getBytes(StandardCharsets.US_ASCII))) {
                throw new InvalidAccessTokenException();
            }

            JsonNode header = objectMapper.readTree(DECODER.decode(parts[0]));
            JsonNode claims = objectMapper.readTree(DECODER.decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !ISSUER.equals(claims.path("iss").asText())
                    || !claims.path("exp").canConvertToLong()
                    || claims.path("exp").asLong() <= clock.instant().getEpochSecond()) {
                throw new InvalidAccessTokenException();
            }
            return UUID.fromString(claims.path("sub").asText());
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private String encodeJson(Object value) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize access token", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required JWT algorithm is unavailable", exception);
        } catch (java.security.InvalidKeyException exception) {
            throw new IllegalStateException("JWT signing key is invalid", exception);
        }
    }
}
