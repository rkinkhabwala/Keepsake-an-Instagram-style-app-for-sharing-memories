package com.keepsake.memory.dto;

import com.keepsake.memory.entity.MemoryStatus;
import java.util.UUID;

public record MemoryStatusDto(UUID id, MemoryStatus status) {
}
