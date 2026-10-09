package com.keepsake.album.dto;

import com.keepsake.album.entity.AlbumRole;
import java.util.UUID;

/** @param role EDITOR (may add their own memories) or VIEWER (may only look) */
public record AddCollaboratorRequest(UUID userId, AlbumRole role) {
}
