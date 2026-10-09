package com.keepsake.album.dto;

import com.keepsake.memory.entity.Visibility;

public record CreateAlbumRequest(String title, String description, Visibility visibility) {
}
