package com.keepsake.memory.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.service.MemoryListService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileMemoryController {

    private final MemoryListService memoryListService;

    public ProfileMemoryController(MemoryListService memoryListService) {
        this.memoryListService = memoryListService;
    }

    /** Profile grid (thumbs only), limited to memories the viewer may see. */
    @GetMapping("/api/users/{username}/memories")
    public CursorPage<MemoryGridItemDto> grid(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return memoryListService.grid(viewerId, username, CursorPageRequest.parse(cursor, limit));
    }
}
