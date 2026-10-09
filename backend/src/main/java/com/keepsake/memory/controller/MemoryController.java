package com.keepsake.memory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.auth.service.RateLimitService;
import com.keepsake.memory.dto.CreateMemoryRequest;
import com.keepsake.memory.dto.MapMemoriesDto;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MemoryStatusDto;
import com.keepsake.memory.dto.UpdateMemoryRequest;
import com.keepsake.memory.service.MemoryListService;
import com.keepsake.memory.service.MemoryService;
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
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final MemoryListService memoryListService;

    public MemoryController(
            MemoryService memoryService,
            RateLimitService rateLimitService,
            ObjectMapper objectMapper,
            MemoryListService memoryListService
    ) {
        this.memoryService = memoryService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.memoryListService = memoryListService;
    }

    /** Memories inside a map viewport; bbox is minLng,minLat,maxLng,maxLat. */
    @GetMapping("/map")
    public MapMemoriesDto map(@AuthenticationPrincipal UUID viewerId, @RequestParam(required = false) String bbox) {
        return memoryListService.map(viewerId, bbox);
    }

    @PostMapping
    public ResponseEntity<MemoryStatusDto> create(
            @AuthenticationPrincipal UUID viewerId,
            @RequestBody CreateMemoryRequest request
    ) {
        rateLimitService.checkMemoryCreate(viewerId);
        return ResponseEntity.accepted().body(memoryService.create(viewerId, request));
    }

    @GetMapping("/{memoryId}")
    public MemoryDto get(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        return memoryService.get(viewerId, memoryId);
    }

    @PatchMapping("/{memoryId}")
    public MemoryDto update(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID memoryId,
            @RequestBody JsonNode body
    ) {
        return memoryService.update(viewerId, memoryId, UpdateMemoryRequest.from(body, objectMapper));
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        memoryService.delete(viewerId, memoryId);
        return ResponseEntity.noContent().build();
    }

    /** Retries processing for a memory that FAILED (spec §6: the author sees a retry option). */
    @PostMapping("/{memoryId}/retry")
    public ResponseEntity<MemoryStatusDto> retry(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID memoryId) {
        return ResponseEntity.accepted().body(memoryService.retry(viewerId, memoryId));
    }
}
