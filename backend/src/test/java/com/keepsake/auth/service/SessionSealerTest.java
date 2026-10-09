package com.keepsake.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.auth.dto.AuthResponse;
import com.keepsake.auth.dto.AuthUserDto;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionSealerTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hmac";

    private final AuthSession session = new AuthSession(
            new AuthResponse("access.jwt.value", "Bearer", 900,
                    new AuthUserDto(UUID.randomUUID(), "ana", "ana@example.test", "Ana", null, true)),
            "raw-refresh-token");

    @Test
    void roundTripsASessionWithoutExposingTheRefreshToken() {
        SessionSealer sealer = new SessionSealer(SECRET, new ObjectMapper());

        String sealed = sealer.seal(session);

        assertThat(sealed).doesNotContain("raw-refresh-token");
        assertThat(sealer.open(sealed)).isEqualTo(session);
    }

    @Test
    void rejectsTamperedValuesAndOtherKeys() {
        SessionSealer sealer = new SessionSealer(SECRET, new ObjectMapper());
        byte[] bytes = Base64.getDecoder().decode(sealer.seal(session));
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> sealer.open(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionSealer(SECRET + "-other", new ObjectMapper()).open(sealer.seal(session)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
