package com.keepsake.auth.service;

import com.keepsake.user.service.TimeZones;
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
    /**
     * A just-rotated refresh token presented again within this window (two tabs refreshing at once) gets the same
     * new session back instead of triggering reuse detection. Later reuse still revokes the whole family.
     */
    public static final Duration REUSE_GRACE = Duration.ofSeconds(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final SessionSealer sessionSealer;
    private final String dummyPasswordHash;
    private final TimeZones timeZones;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock,
            SessionSealer sessionSealer,
            TimeZones timeZones
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.sessionSealer = sessionSealer;
        this.timeZones = timeZones;
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

        User newUser = new User(username, email, passwordEncoder.encode(request.password()),
                request.displayName().trim());
        timeZones.valid(request.timeZone()).ifPresent(newUser::setTimeZone);
        User user = userRepository.saveAndFlush(newUser);
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
        timeZones.valid(request.timeZone()).ifPresent(user.get()::setTimeZone);
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
            Optional<AuthSession> concurrentRotation = sessionWithinGrace(token, now);
            if (concurrentRotation.isPresent()) {
                return concurrentRotation;
            }
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

        IssuedSession successor = issue(user.get(), token.getFamilyId());
        token.rotateTo(successor.tokenId(), sessionSealer.seal(successor.session()), now);
        return Optional.of(successor.session());
    }

    /**
     * The session a token was rotated into, if it was rotated (not logged out) under {@link #REUSE_GRACE} ago and
     * that successor is still live. Requests are serialised by the row lock taken in rotateRefreshToken.
     */
    private Optional<AuthSession> sessionWithinGrace(RefreshToken token, Instant now) {
        if (token.getSuccessorId() == null || token.getSuccessorSession() == null
                || !token.getRevokedAt().plus(REUSE_GRACE).isAfter(now)) {
            return Optional.empty();
        }
        boolean successorLive = refreshTokenRepository.findById(token.getSuccessorId())
                .filter(successor -> successor.getRevokedAt() == null)
                .filter(successor -> successor.getExpiresAt().isAfter(now))
                .isPresent();
        if (!successorLive) {
            return Optional.empty();
        }
        try {
            return Optional.of(sessionSealer.open(token.getSuccessorSession()));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), clock.instant()));
    }

    private record IssuedSession(UUID tokenId, AuthSession session) {
    }

    private AuthSession newSession(User user, UUID familyId) {
        return issue(user, familyId).session();
    }

    private IssuedSession issue(User user, UUID familyId) {
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                hashToken(rawRefreshToken),
                familyId,
                now.plus(REFRESH_TOKEN_LIFETIME)));
        return new IssuedSession(stored.getId(), new AuthSession(
                new AuthResponse(
                        jwtService.createAccessToken(user.getId()),
                        "Bearer",
                        JwtService.ACCESS_TOKEN_LIFETIME.toSeconds(),
                        AuthUserDto.from(user)),
                rawRefreshToken));
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
