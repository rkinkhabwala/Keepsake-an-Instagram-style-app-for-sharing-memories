package com.keepsake.engagement.controller;

import com.keepsake.auth.service.RateLimitService;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.engagement.dto.CommentDto;
import com.keepsake.engagement.dto.CreateCommentRequest;
import com.keepsake.engagement.service.EngagementService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EngagementController {

    private final EngagementService engagementService;
    private final RateLimitService rateLimitService;

    public EngagementController(EngagementService engagementService, RateLimitService rateLimitService) {
        this.engagementService = engagementService;
        this.rateLimitService = rateLimitService;
    }

    @PutMapping("/memories/{memoryId}/like")
    public ResponseEntity<Void> like(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        engagementService.like(viewerId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memories/{memoryId}/like")
    public ResponseEntity<Void> unlike(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        engagementService.unlike(viewerId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/memories/{memoryId}/save")
    public ResponseEntity<Void> save(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        engagementService.save(viewerId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memories/{memoryId}/save")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        engagementService.unsave(viewerId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/memories/{memoryId}/comments")
    public CursorPage<CommentDto> comments(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID memoryId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return engagementService.comments(viewerId, memoryId, CursorPageRequest.parse(cursor, limit));
    }

    @PostMapping("/memories/{memoryId}/comments")
    public ResponseEntity<CommentDto> comment(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID memoryId,
            @RequestBody CreateCommentRequest request
    ) {
        rateLimitService.checkComment(viewerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(engagementService.comment(viewerId, memoryId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID commentId) {
        engagementService.deleteComment(viewerId, commentId);
        return ResponseEntity.noContent().build();
    }
}
