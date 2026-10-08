package com.keepsake.memory.dto;

import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.user.dto.UserSummaryDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemoryDto(
        UUID id,
        UserSummaryDto author,
        String caption,
        LocalDate occurredOn,
        LocationDto location,
        Visibility visibility,
        MemoryStatus status,
        int likeCount,
        int commentCount,
        List<MemoryMediaDto> media,
        List<UserSummaryDto> taggedPeople,
        Instant createdAt
) {
}
