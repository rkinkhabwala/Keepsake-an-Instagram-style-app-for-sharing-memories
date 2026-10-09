package com.keepsake.memory.dto;

import java.util.List;

/** @param truncated true when more memories are in the box than were returned; zoom in to see the rest */
public record MapMemoriesDto(List<MapMemoryDto> items, boolean truncated) {
}
