package com.keepsake.auth.controller;

import com.keepsake.auth.dto.AuthResponse;
import com.keepsake.auth.dto.LoginRequest;
import com.keepsake.auth.dto.SignupRequest;
import com.keepsake.auth.service.AuthService;
import com.keepsake.auth.service.AuthSession;
import com.keepsake.auth.service.RateLimitService;
import com.keepsake.common.error.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "keepsake_refresh";

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            RateLimitService rateLimitService,
            @Value("${keepsake.auth.cookie-secure:false}") boolean secureCookie
    ) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkSignup(clientIp(servletRequest));
        return sessionResponse(authService.signup(request), 201);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkLogin(clientIp(servletRequest));
        return sessionResponse(authService.login(request), 200);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        Optional<AuthSession> rotatedSession = authService.rotateRefreshToken(refreshToken);
        if (rotatedSession.isEmpty()) {
            throw new UnauthenticatedException();
        }
        return sessionResponse(rotatedSession.get(), 200);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> sessionResponse(AuthSession session, int status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(session.refreshToken(), AuthService.REFRESH_TOKEN_LIFETIME).toString())
                .body(session.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return refreshCookie(value, maxAge, secureCookie);
    }

    /** Tells the browser to drop the refresh cookie, e.g. after the account is deleted. */
    public static ResponseCookie expiredRefreshCookie(boolean secure) {
        return refreshCookie("", Duration.ZERO, secure);
    }

    private static ResponseCookie refreshCookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    public static String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
