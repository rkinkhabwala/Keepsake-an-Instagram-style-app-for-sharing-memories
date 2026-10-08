package com.keepsake.user.dto;

import java.util.UUID;

/** Build through {@link com.keepsake.user.service.UserSummaries}, which signs the avatar URL. */
public record UserSummaryDto(UUID id, String username, String displayName, String avatarUrl) {
}
