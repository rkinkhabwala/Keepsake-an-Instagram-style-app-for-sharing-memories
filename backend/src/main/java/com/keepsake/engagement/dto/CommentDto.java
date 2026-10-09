package com.keepsake.engagement.dto;

import com.keepsake.user.dto.UserSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Comment bodies are plain text; clients must render them as text. A deleted top-level comment that still has
 * replies is kept as a placeholder with a null author and body.
 */
public record CommentDto(
        UUID id,
        UUID parentId,
        UserSummaryDto author,
        String body,
        boolean deleted,
        boolean canDelete,
        Instant createdAt,
        List<CommentDto> replies
) {
}
