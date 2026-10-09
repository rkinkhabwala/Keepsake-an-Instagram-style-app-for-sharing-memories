package com.keepsake.album.dto;

import java.util.List;
import java.util.UUID;

public record AddAlbumMemoriesRequest(List<UUID> memoryIds) {
}
