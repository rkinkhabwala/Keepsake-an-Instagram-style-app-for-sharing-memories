package com.keepsake.memory.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A memory as a map marker: where it happened and its first photo's thumb. */
public record MapMemoryDto(
        UUID id,
        double latitude,
        double longitude,
        String locationName,
        LocalDate occurredOn,
        String authorUsername,
        String thumbUrl,
        String blurhash
) {
}
