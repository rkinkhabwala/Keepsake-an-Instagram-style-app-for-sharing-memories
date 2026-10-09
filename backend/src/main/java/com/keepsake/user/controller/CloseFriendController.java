package com.keepsake.user.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.service.CloseFriendService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/close-friends")
public class CloseFriendController {

    private final CloseFriendService closeFriendService;

    public CloseFriendController(CloseFriendService closeFriendService) {
        this.closeFriendService = closeFriendService;
    }

    @GetMapping
    public CursorPage<UserSummaryDto> list(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return closeFriendService.list(viewerId, CursorPageRequest.parse(cursor, limit));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        closeFriendService.add(viewerId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        closeFriendService.remove(viewerId, userId);
        return ResponseEntity.noContent().build();
    }
}
