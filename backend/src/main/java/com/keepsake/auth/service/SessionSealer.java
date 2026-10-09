package com.keepsake.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts an issued session (raw refresh token + access response) with AES-GCM so it can be kept on the rotated
 * token row for the reuse grace window without storing a usable refresh token in plain text. The key is derived
 * from the JWT secret.
 */
@Component
public class SessionSealer {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;
    private final ObjectMapper objectMapper;

    public SessionSealer(@Value("${keepsake.jwt-secret}") String secret, ObjectMapper objectMapper) {
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(("keepsake-refresh-grace:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(material, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        this.objectMapper = objectMapper;
    }

    public String seal(AuthSession session) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(objectMapper.writeValueAsBytes(session));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Could not seal session", exception);
        }
    }

    /** @throws IllegalArgumentException if the value was not sealed with this key or was altered */
    public AuthSession open(String sealed) {
        try {
            byte[] bytes = Base64.getDecoder().decode(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return objectMapper.readValue(plain, AuthSession.class);
        } catch (GeneralSecurityException | IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Sealed session is invalid", exception);
        }
    }
}
