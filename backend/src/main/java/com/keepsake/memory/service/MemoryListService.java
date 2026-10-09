package com.keepsake.memory.service;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidCursorException;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MapMemoriesDto;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.user.entity.User;
import com.keepsake.user.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Memory lists. Every query filters with {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL}. */
@Service
public class MemoryListService {

    private static final Instant SAVED_AFTER_EVERYTHING = Instant.parse("9999-12-31T00:00:00Z");
    private static final UUID MAX_UUID = new UUID(-1L, -1L);

    private final MemoryRepository memoryRepository;
    private final MemoryDtoAssembler assembler;
    private final UserService userService;

    public MemoryListService(MemoryRepository memoryRepository, MemoryDtoAssembler assembler, UserService userService) {
        this.memoryRepository = memoryRepository;
        this.assembler = assembler;
        this.userService = userService;
    }

    /** Home feed (spec §7, Phase 1): fan-out on read, newest first, cursor = last memory id. */
    @Transactional(readOnly = true)
    public CursorPage<MemoryDto> feed(UUID viewerId, CursorPageRequest page) {
        return page(memoryRepository.findFeed(viewerId, page.cursorOrMax(), page.limit() + 1), page,
                rows -> assembler.toDtos(viewerId, rows));
    }

    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> grid(UUID viewerId, String username, CursorPageRequest page) {
        User author = userService.requireViewableByUsername(viewerId, username);
        return page(memoryRepository.findByAuthorVisibleTo(author.getId(), viewerId, page.cursorOrMax(),
                page.limit() + 1), page, assembler::toGridItems);
    }

    /** The "Tagged" profile tab: memories the user was tagged in, limited to those the viewer may see. */
    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> tagged(UUID viewerId, String username, CursorPageRequest page) {
        User tagged = userService.requireViewableByUsername(viewerId, username);
        return page(memoryRepository.findTaggedVisibleTo(tagged.getId(), viewerId, page.cursorOrMax(),
                page.limit() + 1), page, assembler::toGridItems);
    }

    /**
     * The viewer's own saved memories, most recently saved first, that they can still see. The cursor is opaque:
     * {@code <save time in epoch microseconds>.<memory id>}.
     */
    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> saved(UUID viewerId, String cursor, Integer limit) {
        int pageSize = CursorPageRequest.parse(null, limit).limit();
        Instant savedBefore = SAVED_AFTER_EVERYTHING;
        UUID cursorId = MAX_UUID;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\.", 2);
            try {
                savedBefore = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
                cursorId = UUID.fromString(parts[1]);
            } catch (RuntimeException malformed) {
                throw new InvalidCursorException();
            }
        }
        List<Memory> rows = memoryRepository.findSavedVisibleTo(viewerId, savedBefore, cursorId, pageSize + 1);
        if (rows.size() <= pageSize) {
            return new CursorPage<>(assembler.toGridItems(rows), null);
        }
        List<Memory> items = rows.subList(0, pageSize);
        UUID last = items.getLast().getId();
        Instant lastSavedAt = memoryRepository.findSaveTime(viewerId, last).orElseThrow();
        String nextCursor = ChronoUnit.MICROS.between(Instant.EPOCH, lastSavedAt) + "." + last;
        return new CursorPage<>(assembler.toGridItems(items), nextCursor);
    }

    static final int MAP_LIMIT = 500;

    /** Map markers inside a box (spec §8), scoped to own, followed, and tagged memories (Phase 2 decision). */
    @Transactional(readOnly = true)
    public MapMemoriesDto map(UUID viewerId, String bbox) {
        BoundingBox box = BoundingBox.parse(bbox);
        List<Memory> rows = memoryRepository.findOnMap(viewerId, box.minLng(), box.minLat(), box.maxLng(),
                box.maxLat(), box.crossesAntimeridian(), MAP_LIMIT + 1);
        boolean truncated = rows.size() > MAP_LIMIT;
        return new MapMemoriesDto(assembler.toMapItems(truncated ? rows.subList(0, MAP_LIMIT) : rows), truncated);
    }

    /** How many of the author's memories the viewer can see, so hidden memories are not even counted. */
    @Transactional(readOnly = true)
    public long visibleCount(UUID viewerId, UUID authorId) {
        return memoryRepository.countByAuthorVisibleTo(authorId, viewerId);
    }

    private static <T> CursorPage<T> page(
            List<Memory> rows,
            CursorPageRequest page,
            Function<List<Memory>, List<T>> toDtos
    ) {
        CursorPage<Memory> memories = CursorPage.fromLookahead(rows, page.limit(), Memory::getId);
        return new CursorPage<>(toDtos.apply(memories.items()), memories.nextCursor());
    }
}
