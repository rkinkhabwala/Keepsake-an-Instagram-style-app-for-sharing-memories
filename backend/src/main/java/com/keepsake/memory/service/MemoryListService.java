package com.keepsake.memory.service;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.user.entity.User;
import com.keepsake.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Memory lists. Every query filters with {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL}. */
@Service
public class MemoryListService {

    private final MemoryRepository memoryRepository;
    private final MemoryCursor memoryCursor;
    private final MemoryDtoAssembler assembler;
    private final UserService userService;

    public MemoryListService(
            MemoryRepository memoryRepository,
            MemoryCursor memoryCursor,
            MemoryDtoAssembler assembler,
            UserService userService
    ) {
        this.memoryRepository = memoryRepository;
        this.memoryCursor = memoryCursor;
        this.assembler = assembler;
        this.userService = userService;
    }

    /** Home feed (spec §7, Phase 1): fan-out on read, newest first. */
    @Transactional(readOnly = true)
    public CursorPage<MemoryDto> feed(UUID viewerId, CursorPageRequest page) {
        MemoryCursor.Position position = memoryCursor.resolve(page);
        List<Memory> rows = memoryRepository.findFeed(
                viewerId, position.createdAt(), position.id(), page.limit() + 1);
        CursorPage<Memory> memories = CursorPage.fromLookahead(rows, page.limit(), Memory::getId);
        return new CursorPage<>(assembler.toDtos(memories.items()), memories.nextCursor());
    }

    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> grid(UUID viewerId, String username, CursorPageRequest page) {
        User author = userService.requireByUsername(username);
        MemoryCursor.Position position = memoryCursor.resolve(page);
        List<Memory> rows = memoryRepository.findByAuthorVisibleTo(
                author.getId(), viewerId, position.createdAt(), position.id(), page.limit() + 1);
        CursorPage<Memory> memories = CursorPage.fromLookahead(rows, page.limit(), Memory::getId);
        return new CursorPage<>(assembler.toGridItems(memories.items()), memories.nextCursor());
    }

    /** How many of the author's memories the viewer can see, so hidden memories are not even counted. */
    @Transactional(readOnly = true)
    public long visibleCount(UUID viewerId, UUID authorId) {
        return memoryRepository.countByAuthorVisibleTo(authorId, viewerId);
    }
}
