package com.keepsake.memory.dto;

import com.keepsake.memory.entity.Visibility;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param occurredOn optional; defaults to the first photo's EXIF date, else today
 * @param uploadIds  1–10 upload ids in display order
 */
public record CreateMemoryRequest(
        String caption,
        LocalDate occurredOn,
        LocationDto location,
        Visibility visibility,
        List<String> taggedUsernames,
        List<UUID> uploadIds
) {
}
