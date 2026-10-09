package com.keepsake.album.service;

import com.keepsake.album.dto.AddCollaboratorRequest;
import com.keepsake.album.dto.AlbumDto;
import com.keepsake.album.dto.CollaboratorDto;
import com.keepsake.album.dto.CreateAlbumRequest;
import com.keepsake.album.dto.UpdateAlbumRequest;
import com.keepsake.album.entity.Album;
import com.keepsake.album.entity.AlbumRole;
import com.keepsake.album.repository.AlbumRepository;
import com.keepsake.common.error.ForbiddenException;
import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidCursorException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryAccessPolicy;
import com.keepsake.memory.service.MemoryDtoAssembler;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.BlockRepository;
import com.keepsake.user.service.FollowService;
import com.keepsake.user.service.UserService;
import com.keepsake.user.service.UserSummaries;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Albums (spec Phase 2): an owner's collection with a visibility, optionally shared with collaborators. Who may
 * see an album uses {@link MemoryAccessPolicy#canViewOwnedContent}; which of its memories they see is filtered
 * per memory with the policy's SQL. Anyone who cannot see an album gets 404; someone who can see it but may not
 * change it gets 403.
 */
@Service
public class AlbumService {

    static final int MAX_TITLE = 100;
    static final int MAX_DESCRIPTION = 500;
    static final int MAX_ADD = 50;
    static final int MAX_LISTED = 100;

    private final AlbumRepository albumRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryAccessPolicy accessPolicy;
    private final MemoryDtoAssembler memoryAssembler;
    private final UserService userService;
    private final UserSummaries userSummaries;
    private final FollowService followService;
    private final BlockRepository blockRepository;

    public AlbumService(
            AlbumRepository albumRepository,
            MemoryRepository memoryRepository,
            MemoryAccessPolicy accessPolicy,
            MemoryDtoAssembler memoryAssembler,
            UserService userService,
            UserSummaries userSummaries,
            FollowService followService,
            BlockRepository blockRepository
    ) {
        this.albumRepository = albumRepository;
        this.memoryRepository = memoryRepository;
        this.accessPolicy = accessPolicy;
        this.memoryAssembler = memoryAssembler;
        this.userService = userService;
        this.userSummaries = userSummaries;
        this.followService = followService;
        this.blockRepository = blockRepository;
    }

    private record Access(Album album, User owner, AlbumRole role) {
    }

    @Transactional
    public AlbumDto create(UUID ownerId, CreateAlbumRequest request) {
        if (request.visibility() == null) {
            throw new InvalidRequestException("visibility is required");
        }
        Album album = albumRepository.save(
                new Album(ownerId, title(request.title()), description(request.description()), request.visibility()));
        return toDto(new Access(album, userService.requireById(ownerId), AlbumRole.OWNER), ownerId);
    }

    @Transactional(readOnly = true)
    public AlbumDto get(UUID viewerId, UUID albumId) {
        return toDto(requireViewable(viewerId, albumId, false), viewerId);
    }

    @Transactional
    public AlbumDto update(UUID viewerId, UUID albumId, UpdateAlbumRequest request) {
        Access access = requireRole(viewerId, albumId, AlbumRole.OWNER);
        Album album = access.album();
        if (request.title() != null) {
            album.setTitle(title(request.title()));
        }
        if (request.description() != null) {
            album.setDescription(description(request.description()));
        }
        if (request.visibility() != null) {
            album.setVisibility(request.visibility());
        }
        if (request.coverMemoryId() != null) {
            if (albumRepository.findPosition(albumId, request.coverMemoryId()).isEmpty()) {
                throw new InvalidRequestException("The cover must be a memory in this album");
            }
            album.setCoverMemoryId(request.coverMemoryId());
        }
        albumRepository.saveAndFlush(album);
        return toDto(access, viewerId);
    }

    @Transactional
    public void delete(UUID viewerId, UUID albumId) {
        albumRepository.delete(requireRole(viewerId, albumId, AlbumRole.OWNER).album());
    }

    /** Memories the viewer could see on their own, in album order; the cursor is an album position. */
    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> memories(UUID viewerId, UUID albumId, String cursor, Integer limit) {
        requireViewable(viewerId, albumId, false);
        int pageSize = CursorPageRequest.parse(null, limit).limit();
        int after = -1;
        if (cursor != null && !cursor.isBlank()) {
            try {
                after = Integer.parseInt(cursor.strip());
            } catch (NumberFormatException malformed) {
                throw new InvalidCursorException();
            }
        }
        List<Memory> rows = albumRepository.findVisibleMemories(albumId, viewerId, after, pageSize + 1);
        if (rows.size() <= pageSize) {
            return new CursorPage<>(memoryAssembler.toGridItems(rows), null);
        }
        List<Memory> items = rows.subList(0, pageSize);
        int lastPosition = albumRepository.findPosition(albumId, items.getLast().getId()).orElseThrow();
        return new CursorPage<>(memoryAssembler.toGridItems(items), Integer.toString(lastPosition));
    }

    /** The owner and EDITOR collaborators may add memories they authored, appended in the given order. */
    @Transactional
    public void addMemories(UUID viewerId, UUID albumId, List<UUID> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty() || memoryIds.size() > MAX_ADD) {
            throw new InvalidRequestException("Add between 1 and " + MAX_ADD + " memories at a time");
        }
        Access access = requireEditor(viewerId, albumId);
        Set<UUID> distinct = new LinkedHashSet<>(memoryIds);
        for (UUID memoryId : distinct) {
            Optional<Memory> memory = memoryRepository.findById(memoryId);
            if (memory.isEmpty() || memory.get().isDeleted() || !memory.get().getAuthorId().equals(viewerId)) {
                throw new InvalidRequestException("You can only add your own memories");
            }
        }
        int position = albumRepository.maxPosition(access.album().getId());
        for (UUID memoryId : distinct) {
            if (albumRepository.findPosition(albumId, memoryId).isEmpty()) {
                albumRepository.insertMemory(albumId, memoryId, ++position, viewerId);
            }
        }
    }

    /** The owner may remove any memory; an editor only the ones they added. */
    @Transactional
    public void removeMemory(UUID viewerId, UUID albumId, UUID memoryId) {
        Access access = requireViewable(viewerId, albumId, true);
        UUID addedBy = albumRepository.findAddedBy(albumId, memoryId)
                .orElseThrow(() -> new NotFoundException("Memory not found in this album"));
        boolean allowed = access.role() == AlbumRole.OWNER
                || (access.role() == AlbumRole.EDITOR && addedBy.equals(viewerId));
        if (!allowed) {
            throw new ForbiddenException("You can't remove this memory from the album");
        }
        albumRepository.deleteMemory(albumId, memoryId);
        if (memoryId.equals(access.album().getCoverMemoryId())) {
            access.album().setCoverMemoryId(null);
        }
    }

    /** The owner adds one of their followers directly, with no invite step (Phase 2 decision). */
    @Transactional
    public void addCollaborator(UUID viewerId, UUID albumId, AddCollaboratorRequest request) {
        Access access = requireRole(viewerId, albumId, AlbumRole.OWNER);
        if (request.userId() == null || request.role() == null || !request.role().isCollaborator()) {
            throw new InvalidRequestException("Give a userId and a role of EDITOR or VIEWER");
        }
        if (request.userId().equals(viewerId)) {
            throw new InvalidRequestException("You already own this album");
        }
        userService.requireViewableById(viewerId, request.userId());
        if (!followService.isAcceptedFollower(request.userId(), access.owner().getId())) {
            throw new InvalidRequestException("Only people who follow you can be added to an album");
        }
        albumRepository.upsertCollaborator(albumId, request.userId(), request.role().name());
    }

    /** The owner removes a collaborator; the memories that collaborator added leave the album with them. */
    @Transactional
    public void removeCollaborator(UUID viewerId, UUID albumId, UUID userId) {
        requireRole(viewerId, albumId, AlbumRole.OWNER);
        albumRepository.deleteCollaborator(albumId, userId);
        albumRepository.deleteMemoriesAddedBy(albumId, userId);
    }

    /** A collaborator leaves; the memories they added leave the album with them. */
    @Transactional
    public void leave(UUID viewerId, UUID albumId) {
        Access access = requireViewable(viewerId, albumId, true);
        if (!access.role().isCollaborator()) {
            throw new NotFoundException("You are not a collaborator on this album");
        }
        albumRepository.deleteCollaborator(albumId, viewerId);
        albumRepository.deleteMemoriesAddedBy(albumId, viewerId);
    }

    /** A profile's Albums tab: albums the user owns or collaborates on that the viewer may see. */
    @Transactional(readOnly = true)
    public List<AlbumDto> forProfile(UUID viewerId, String username) {
        User user = userService.requireViewableByUsername(viewerId, username);
        return albumRepository.findOwnedOrCollaborating(user.getId(), MAX_LISTED).stream()
                .map(album -> access(viewerId, album))
                .flatMap(Optional::stream)
                .map(access -> toDto(access, viewerId))
                .toList();
    }

    private Access requireViewable(UUID viewerId, UUID albumId, boolean forUpdate) {
        Album album = (forUpdate ? albumRepository.findByIdForUpdate(albumId) : albumRepository.findById(albumId))
                .orElseThrow(AlbumService::notFound);
        return access(viewerId, album).orElseThrow(AlbumService::notFound);
    }

    private Access requireEditor(UUID viewerId, UUID albumId) {
        Access access = requireViewable(viewerId, albumId, true);
        if (!access.role().canEdit()) {
            throw new ForbiddenException("You can't add memories to this album");
        }
        return access;
    }

    private Access requireRole(UUID viewerId, UUID albumId, AlbumRole role) {
        Access access = requireViewable(viewerId, albumId, true);
        if (access.role() != role) {
            throw new ForbiddenException("Only the album's owner can do that");
        }
        return access;
    }

    /** Owner and collaborators always see the album; anyone else only through its visibility (including blocks). */
    private Optional<Access> access(UUID viewerId, Album album) {
        User owner = userService.requireById(album.getOwnerId());
        if (owner.getId().equals(viewerId)) {
            return Optional.of(new Access(album, owner, AlbumRole.OWNER));
        }
        Optional<String> role = albumRepository.findRole(album.getId(), viewerId);
        if (role.isPresent()) {
            return Optional.of(new Access(album, owner, AlbumRole.valueOf(role.get())));
        }
        if (accessPolicy.canViewOwnedContent(viewerId, owner.getId(), owner.isPrivate(), album.getVisibility())) {
            return Optional.of(new Access(album, owner, AlbumRole.NONE));
        }
        return Optional.empty();
    }

    private AlbumDto toDto(Access access, UUID viewerId) {
        Album album = access.album();
        List<CollaboratorDto> collaborators = albumRepository.findCollaborators(album.getId()).stream()
                .filter(row -> row.getUserId().equals(viewerId)
                        || !blockRepository.existsBetween(viewerId, row.getUserId()))
                .map(row -> new CollaboratorDto(
                        userSummaries.of(userService.requireById(row.getUserId())), AlbumRole.valueOf(row.getRole())))
                .toList();
        MemoryGridItemDto cover = cover(album, viewerId);
        return new AlbumDto(
                album.getId(),
                album.getTitle(),
                album.getDescription(),
                album.getVisibility(),
                userSummaries.of(access.owner()),
                access.role(),
                collaborators,
                cover == null ? null : cover.thumbUrl(),
                cover == null ? null : cover.blurhash(),
                albumRepository.countVisibleMemories(album.getId(), viewerId),
                album.getCreatedAt());
    }

    /** The chosen cover if the viewer may see it, otherwise the first album memory they may see. */
    private MemoryGridItemDto cover(Album album, UUID viewerId) {
        Optional<Memory> chosen = Optional.ofNullable(album.getCoverMemoryId())
                .filter(id -> memoryRepository.isVisibleTo(id, viewerId))
                .flatMap(memoryRepository::findById);
        List<Memory> memory = chosen.map(List::of)
                .orElseGet(() -> albumRepository.findVisibleMemories(album.getId(), viewerId, -1, 1));
        return memory.isEmpty() ? null : memoryAssembler.toGridItems(memory).getFirst();
    }

    private static String title(String value) {
        String title = value == null ? "" : value.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new InvalidRequestException("title must be 1 to " + MAX_TITLE + " characters");
        }
        return title;
    }

    private static String description(String value) {
        if (value == null) {
            return null;
        }
        String description = value.strip();
        if (description.length() > MAX_DESCRIPTION) {
            throw new InvalidRequestException("description must be at most " + MAX_DESCRIPTION + " characters");
        }
        return description.isEmpty() ? null : description;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Album not found");
    }
}
