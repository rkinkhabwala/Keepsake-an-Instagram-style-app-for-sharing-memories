package com.keepsake.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.auth.controller.AuthController;
import com.keepsake.auth.entity.RefreshToken;
import com.keepsake.auth.repository.RefreshTokenRepository;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AuthIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Keepsake-password-2026";
    private static final AtomicInteger NEXT_TEST_IP = new AtomicInteger(30);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    void signupHashesPasswordReturnsAccessTokenAndSetsHttpOnlyRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddress("198.51.100.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("alice", "Alice@example.test", "Alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.email").value("alice@example.test"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict"),
                                org.hamcrest.Matchers.containsString("Path=/api/auth"))))
                .andReturn();

        User user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(
                "alice@example.test", "alice").orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(cookieValue(result)).isNotBlank();
    }

    @Test
    void rotatingRefreshTokenAndReusingOldTokenRevokesItsFamily() throws Exception {
        MvcResult signup = signup("refresh_user", "refresh@example.test", "Refresh");
        String oldRawToken = cookieValue(signup);

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_COOKIE_NAME, oldRawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        String newRawToken = cookieValue(rotated);
        assertThat(newRawToken).isNotEqualTo(oldRawToken);

        RefreshToken oldRecord = refreshTokenRepository.findByTokenHash(hash(oldRawToken)).orElseThrow();
        RefreshToken newRecord = refreshTokenRepository.findByTokenHash(hash(newRawToken)).orElseThrow();
        assertThat(oldRecord.getRevokedAt()).isNotNull();
        assertThat(newRecord.getFamilyId()).isEqualTo(oldRecord.getFamilyId());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_COOKIE_NAME, oldRawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        List<RefreshToken> family = refreshTokenRepository.findAllByFamilyId(oldRecord.getFamilyId());
        assertThat(family).hasSize(2).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void loginAcceptsUsernameAndRejectsBadCredentials() throws Exception {
        signup("login_user", "login@example.test", "Login");

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress("198.51.100.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("identifier", "login_user", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("login_user"));

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress("198.51.100.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("identifier", "login_user", "password", "incorrect-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void signupRejectsDuplicateEmailAndInvalidInputWithProblemDetails() throws Exception {
        signup("duplicate_user", "duplicate@example.test", "Duplicate");

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddress("198.51.100.4"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("duplicate_user2", "DUPLICATE@example.test", "Duplicate Two")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddress("198.51.100.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Bad Name","email":"not-email","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void appliesConfiguredLoginAndSignupLimitsPerIp() throws Exception {
        RequestPostProcessor loginIp = remoteAddress("198.51.100.21");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(loginIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifier":"nobody","password":"a-long-enough-password"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .with(loginIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"nobody","password":"a-long-enough-password"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        for (int attempt = 0; attempt < 3; attempt++) {
            signup("signuplimit" + attempt, "signuplimit" + attempt + "@example.test", "Signup limit",
                    "198.51.100.22");
        }
        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddress("198.51.100.22"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("signuplimit4", "signuplimit4@example.test", "Signup limit")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void spoofedForwardedForFromNonProxyIsIgnoredForRateLimiting() throws Exception {
        // A client talking to the backend directly cannot pick a fresh address per attempt.
        for (int attempt = 0; attempt < 5; attempt++) {
            badLogin(remoteAddress("198.51.100.40"), "203.0.113." + attempt).andExpect(status().isUnauthorized());
        }
        badLogin(remoteAddress("198.51.100.40"), "203.0.113.99").andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedForFromTrustedProxyIdentifiesEachClient() throws Exception {
        // Behind the proxy, each real client gets its own limit...
        for (int client = 0; client < 6; client++) {
            badLogin(remoteAddress(TRUSTED_PROXY), "198.51.100." + (60 + client)).andExpect(status().isUnauthorized());
        }
        // ...and one client cannot exceed it by going through the proxy.
        for (int attempt = 0; attempt < 4; attempt++) {
            badLogin(remoteAddress(TRUSTED_PROXY), "198.51.100.60").andExpect(status().isUnauthorized());
        }
        badLogin(remoteAddress(TRUSTED_PROXY), "198.51.100.60").andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions badLogin(RequestPostProcessor from, String forwardedFor)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(from)
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier":"nobody","password":"a-long-enough-password"}
                        """));
    }

    @Test
    void logoutRevokesTokenFamilyAndClearsCookie() throws Exception {
        MvcResult signup = signup("logout_user", "logout@example.test", "Logout");
        String refreshToken = cookieValue(signup);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(refreshToken)).orElseThrow();
        assertThat(token.getRevokedAt()).isNotNull();
    }

    private MvcResult signup(String username, String email, String displayName) throws Exception {
        return signup(username, email, displayName, "203.0.113." + NEXT_TEST_IP.incrementAndGet());
    }

    private MvcResult signup(String username, String email, String displayName, String ipAddress) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddress(ipAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(username, email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String signupJson(String username, String email, String displayName) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "email", email,
                "password", PASSWORD,
                "displayName", displayName));
    }

    private static RequestPostProcessor remoteAddress(String ipAddress) {
        return request -> {
            request.setRemoteAddr(ipAddress);
            return request;
        };
    }

    private static String cookieValue(MvcResult result) {
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).isNotNull();
        String namePrefix = AuthController.REFRESH_COOKIE_NAME + "=";
        assertThat(cookie).startsWith(namePrefix);
        return cookie.substring(namePrefix.length(), cookie.indexOf(';'));
    }

    private static String hash(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
