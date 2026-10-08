package com.keepsake.memory.service;

import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidCursorException;
import com.keepsake.memory.repository.MemoryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a memory-id cursor to the (created_at, id) position that list queries page below. The first page
 * starts above every memory.
 */
@Component
public class MemoryCursor {

    private static final Instant AFTER_EVERYTHING = Instant.parse("9999-12-31T00:00:00Z");
    private static final UUID MAX_UUID = new UUID(-1L, -1L);

    private final MemoryRepository memoryRepository;

    public MemoryCursor(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public record Position(Instant createdAt, UUID id) {
    }

    public Position resolve(CursorPageRequest page) {
        if (page.cursor() == null) {
            return new Position(AFTER_EVERYTHING, MAX_UUID);
        }
        return memoryRepository.findById(page.cursor())
                .map(memory -> new Position(memory.getCreatedAt(), memory.getId()))
                .orElseThrow(InvalidCursorException::new);
    }
}
