package com.keepsake.album.dto;

import com.keepsake.album.entity.AlbumRole;
import com.keepsake.user.dto.UserSummaryDto;

public record CollaboratorDto(UserSummaryDto user, AlbumRole role) {
}
