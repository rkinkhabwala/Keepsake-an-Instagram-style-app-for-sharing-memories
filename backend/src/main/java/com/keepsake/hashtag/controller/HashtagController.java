package com.keepsake.hashtag.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.hashtag.service.HashtagService;
import com.keepsake.memory.dto.MemoryGridItemDto;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HashtagController {

    private final HashtagService hashtagService;

    public HashtagController(HashtagService hashtagService) {
        this.hashtagService = hashtagService;
    }

    @GetMapping("/api/hashtags/{tag}/memories")
    public CursorPage<MemoryGridItemDto> memories(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable String tag,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return hashtagService.memories(viewerId, tag, CursorPageRequest.parse(cursor, limit));
    }
}
