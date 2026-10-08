package com.keepsake.user.dto;

import com.keepsake.user.entity.User;
import java.util.UUID;

public record UserSummaryDto(UUID id, String username, String displayName) {

    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
