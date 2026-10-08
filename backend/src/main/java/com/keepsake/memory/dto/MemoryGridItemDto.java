package com.keepsake.memory.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A profile-grid tile: the first photo's square thumb and how many photos the memory has. */
public record MemoryGridItemDto(UUID id, String thumbUrl, String blurhash, int mediaCount, LocalDate occurredOn) {
}
