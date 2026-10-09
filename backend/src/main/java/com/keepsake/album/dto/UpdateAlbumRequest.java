package com.keepsake.album.dto;

import com.keepsake.memory.entity.Visibility;
import java.util.UUID;

/** Null fields are unchanged; an empty description clears it; coverMemoryId must be a memory in the album. */
public record UpdateAlbumRequest(String title, String description, Visibility visibility, UUID coverMemoryId) {
}
