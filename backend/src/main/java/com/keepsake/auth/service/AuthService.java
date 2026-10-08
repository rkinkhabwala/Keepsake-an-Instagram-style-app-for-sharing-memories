package com.keepsake.auth.service;

import com.keepsake.auth.dto.AuthResponse;
import com.keepsake.auth.dto.AuthUserDto;
import com.keepsake.auth.dto.LoginRequest;
import com.keepsake.auth.dto.SignupRequest;
import com.keepsake.auth.entity.RefreshToken;
import com.keepsake.auth.repository.RefreshTokenRepository;
import com.keepsake.common.error.ConflictException;
import com.keepsake.common.error.UnauthenticatedException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.common.id.UuidV7;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(14);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSession signup(SignupRequest request) {
        String username = request.username();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must be no more than 72 UTF-8 bytes");
        }

        User user = userRepository.saveAndFlush(new User(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        return newSession(user, UuidV7.generate());
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(
                request.identifier().trim(), request.identifier().trim());
        String encodedHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), encodedHash);
        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthenticatedException();
        }
        return newSession(user.get(), UuidV7.generate());
    }

    @Transactional
    public Optional<AuthSession> rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawToken));
        if (storedToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = storedToken.get();
        if (token.getRevokedAt() != null) {
            refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), now);
            return Optional.empty();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.revoke(now);
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(token.getUserId());
        if (user.isEmpty()) {
            refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), now);
            return Optional.empty();
        }

        token.revoke(now);
        return Optional.of(newSession(user.get(), token.getFamilyId()));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), clock.instant()));
    }

    private AuthSession newSession(User user, UUID familyId) {
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                hashToken(rawRefreshToken),
                familyId,
                now.plus(REFRESH_TOKEN_LIFETIME)));
        return new AuthSession(
                new AuthResponse(
                        jwtService.createAccessToken(user.getId()),
                        "Bearer",
                        JwtService.ACCESS_TOKEN_LIFETIME.toSeconds(),
                        AuthUserDto.from(user)),
                rawRefreshToken);
    }

    private static String newRefreshToken() {
        byte[] token = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }
}
