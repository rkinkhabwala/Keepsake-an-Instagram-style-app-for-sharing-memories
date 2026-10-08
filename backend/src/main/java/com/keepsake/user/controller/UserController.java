package com.keepsake.user.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.user.dto.FollowResponse;
import com.keepsake.user.dto.MeDto;
import com.keepsake.user.dto.ProfileDto;
import com.keepsake.user.dto.UpdateProfileRequest;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.service.FollowService;
import com.keepsake.user.service.ProfileService;
import com.keepsake.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
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

    public UserController(UserService userService, ProfileService profileService, FollowService followService) {
        this.userService = userService;
        this.profileService = profileService;
        this.followService = followService;
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
