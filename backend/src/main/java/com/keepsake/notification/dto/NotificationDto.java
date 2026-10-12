package com.keepsake.notification.dto;

import com.keepsake.notification.entity.NotificationType;
import com.keepsake.user.dto.UserSummaryDto;
import java.time.Instant;
import java.util.UUID;

/**
 * One notification. For grouped likes, {@code actor} is the latest liker and {@code othersCount} how many others
 * liked since the recipient last read their notifications ("ana and 4 others liked your memory").
 *
 * @param memoryThumbUrl the memory's first thumbnail, when there is a memory
 * @param commentPreview the start of the comment, for COMMENT
 * @param at             when it last changed
 */
public record NotificationDto(
        UUID id,
        NotificationType type,
        UserSummaryDto actor,
        int othersCount,
        UUID memoryId,
        String memoryThumbUrl,
        String commentPreview,
        boolean read,
        Instant at
) {
}
