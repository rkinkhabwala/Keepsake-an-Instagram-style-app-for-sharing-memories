package com.keepsake.album.controller;

import com.keepsake.album.dto.AddAlbumMemoriesRequest;
import com.keepsake.album.dto.AddCollaboratorRequest;
import com.keepsake.album.dto.AlbumDto;
import com.keepsake.album.dto.CreateAlbumRequest;
import com.keepsake.album.dto.UpdateAlbumRequest;
import com.keepsake.album.service.AlbumService;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.memory.dto.MemoryGridItemDto;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @PostMapping("/api/albums")
    public ResponseEntity<AlbumDto> create(@AuthenticationPrincipal UUID viewerId, @RequestBody CreateAlbumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(viewerId, request));
    }

    @GetMapping("/api/albums/{albumId}")
    public AlbumDto get(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID albumId) {
        return albumService.get(viewerId, albumId);
    }

    @PatchMapping("/api/albums/{albumId}")
    public AlbumDto update(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @RequestBody UpdateAlbumRequest request
    ) {
        return albumService.update(viewerId, albumId, request);
    }

    @DeleteMapping("/api/albums/{albumId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID albumId) {
        albumService.delete(viewerId, albumId);
        return ResponseEntity.noContent().build();
    }

    /** The memories the viewer may see, in album order; the cursor is opaque. */
    @GetMapping("/api/albums/{albumId}/memories")
    public CursorPage<MemoryGridItemDto> memories(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return albumService.memories(viewerId, albumId, cursor, limit);
    }

    @PostMapping("/api/albums/{albumId}/memories")
    public ResponseEntity<Void> addMemories(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @RequestBody AddAlbumMemoriesRequest request
    ) {
        albumService.addMemories(viewerId, albumId, request.memoryIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/albums/{albumId}/memories/{memoryId}")
    public ResponseEntity<Void> removeMemory(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @PathVariable UUID memoryId
    ) {
        albumService.removeMemory(viewerId, albumId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/albums/{albumId}/collaborators")
    public ResponseEntity<Void> addCollaborator(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @RequestBody AddCollaboratorRequest request
    ) {
        albumService.addCollaborator(viewerId, albumId, request);
        return ResponseEntity.noContent().build();
    }

    /** A collaborator leaves the album. */
    @DeleteMapping("/api/albums/{albumId}/collaborators/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID albumId) {
        albumService.leave(viewerId, albumId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/albums/{albumId}/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable UUID albumId,
            @PathVariable UUID userId
    ) {
        albumService.removeCollaborator(viewerId, albumId, userId);
        return ResponseEntity.noContent().build();
    }

    /** A profile's Albums tab (spec §9): albums the user owns or collaborates on that the viewer may see. */
    @GetMapping("/api/users/{username}/albums")
    public CursorPage<AlbumDto> forProfile(@AuthenticationPrincipal UUID viewerId, @PathVariable String username) {
        return new CursorPage<>(albumService.forProfile(viewerId, username), null);
    }
}
