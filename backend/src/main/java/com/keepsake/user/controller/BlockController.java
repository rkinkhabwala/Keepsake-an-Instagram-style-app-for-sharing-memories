package com.keepsake.user.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.service.BlockService;
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
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    /** The accounts the caller has blocked (not who has blocked them, which is never revealed). */
    @GetMapping
    public CursorPage<UserSummaryDto> list(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return blockService.blocked(viewerId, CursorPageRequest.parse(cursor, limit));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> block(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        blockService.block(viewerId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID userId) {
        blockService.unblock(viewerId, userId);
        return ResponseEntity.noContent().build();
    }
}
