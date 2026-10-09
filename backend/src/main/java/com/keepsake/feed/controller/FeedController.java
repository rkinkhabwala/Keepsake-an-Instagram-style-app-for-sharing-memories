package com.keepsake.feed.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.feed.service.FeedService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/api/feed")
    public CursorPage<MemoryDto> feed(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return feedService.feed(viewerId, CursorPageRequest.parse(cursor, limit));
    }
}
