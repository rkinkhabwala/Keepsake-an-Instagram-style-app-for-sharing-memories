package com.keepsake.resurface.dto;

import com.keepsake.memory.dto.MemoryGridItemDto;
import java.util.List;

/** Memories from one earlier year, e.g. "3 years ago". */
public record OnThisDayYearDto(int year, int yearsAgo, List<MemoryGridItemDto> memories) {
}
