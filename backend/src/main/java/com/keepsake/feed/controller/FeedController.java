package com.keepsake.feed.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.service.MemoryListService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {

    private final MemoryListService memoryListService;

    public FeedController(MemoryListService memoryListService) {
        this.memoryListService = memoryListService;
    }

    @GetMapping("/api/feed")
    public CursorPage<MemoryDto> feed(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return memoryListService.feed(viewerId, CursorPageRequest.parse(cursor, limit));
    }
}
