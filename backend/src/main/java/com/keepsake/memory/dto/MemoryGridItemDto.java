package com.keepsake.memory.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A profile-grid tile: the first item's square thumb, how many items the memory has, and whether that first item
 * is a video.
 */
public record MemoryGridItemDto(
        UUID id,
        String thumbUrl,
        String blurhash,
        int mediaCount,
        LocalDate occurredOn,
        boolean video
) {
}
