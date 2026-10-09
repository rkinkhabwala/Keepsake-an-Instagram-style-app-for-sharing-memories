package com.keepsake.user.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.service.FollowService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-requests")
public class FollowRequestController {

    private final FollowService followService;

    public FollowRequestController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping
    public CursorPage<UserSummaryDto> pending(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return followService.pendingRequests(viewerId, CursorPageRequest.parse(cursor, limit));
    }

    @PostMapping("/{userId}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        followService.acceptRequest(viewerId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        followService.declineRequest(viewerId, userId);
        return ResponseEntity.noContent().build();
    }
}
