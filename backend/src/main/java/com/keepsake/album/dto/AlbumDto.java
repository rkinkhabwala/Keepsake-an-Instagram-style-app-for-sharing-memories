package com.keepsake.album.dto;

import com.keepsake.album.entity.AlbumRole;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.user.dto.UserSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param coverThumbUrl the cover memory's thumb if the viewer may see it, otherwise their first visible memory's
 * @param memoryCount   how many of the album's memories the viewer may see
 */
public record AlbumDto(
        UUID id,
        String title,
        String description,
        Visibility visibility,
        UserSummaryDto owner,
        AlbumRole viewerRole,
        List<CollaboratorDto> collaborators,
        String coverThumbUrl,
        String coverBlurhash,
        long memoryCount,
        Instant createdAt
) {
}
