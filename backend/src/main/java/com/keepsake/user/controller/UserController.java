package com.keepsake.user.controller;

import com.keepsake.auth.controller.AuthController;
import com.keepsake.auth.service.RateLimitService;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.user.dto.DeleteAccountRequest;
import com.keepsake.user.dto.FollowResponse;
import com.keepsake.user.dto.MeDto;
import com.keepsake.user.dto.ProfileDto;
import com.keepsake.user.dto.UpdateProfileRequest;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.service.AccountDeletionService;
import com.keepsake.user.service.FollowService;
import com.keepsake.user.service.ProfileService;
import com.keepsake.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final FollowService followService;
    private final AccountDeletionService accountDeletionService;
    private final RateLimitService rateLimitService;
    private final boolean secureCookie;

    public UserController(
            UserService userService,
            ProfileService profileService,
            FollowService followService,
            AccountDeletionService accountDeletionService,
            RateLimitService rateLimitService,
            @Value("${keepsake.auth.cookie-secure:false}") boolean secureCookie
    ) {
        this.userService = userService;
        this.profileService = profileService;
        this.followService = followService;
        this.accountDeletionService = accountDeletionService;
        this.rateLimitService = rateLimitService;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal UUID viewerId) {
        return userService.me(viewerId);
    }

    @PatchMapping("/me")
    public MeDto updateMe(
            @AuthenticationPrincipal UUID viewerId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateMe(viewerId, request);
    }

    /** Deletes the account and everything in it. Password attempts count against the login rate limit. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal UUID viewerId,
            @RequestBody DeleteAccountRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkLogin(AuthController.clientIp(servletRequest));
        accountDeletionService.deleteAccount(viewerId, request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthController.expiredRefreshCookie(secureCookie).toString())
                .build();
    }

    @GetMapping("/{username}")
    public ProfileDto profile(@AuthenticationPrincipal UUID viewerId, @PathVariable String username) {
        return profileService.profile(viewerId, username);
    }

    @PostMapping("/{username}/follow")
    public FollowResponse follow(@AuthenticationPrincipal UUID viewerId, @PathVariable String username) {
        return new FollowResponse(followService.follow(viewerId, username));
    }

    @DeleteMapping("/{username}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal UUID viewerId, @PathVariable String username) {
        followService.unfollow(viewerId, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}/followers")
    public CursorPage<UserSummaryDto> followers(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return followService.followers(viewerId, username, CursorPageRequest.parse(cursor, limit));
    }

    @GetMapping("/{username}/following")
    public CursorPage<UserSummaryDto> following(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return followService.following(viewerId, username, CursorPageRequest.parse(cursor, limit));
    }
}
