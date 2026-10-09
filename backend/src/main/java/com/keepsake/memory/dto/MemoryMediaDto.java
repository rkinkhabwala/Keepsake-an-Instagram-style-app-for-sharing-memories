package com.keepsake.memory.dto;

import com.keepsake.memory.entity.MediaType;

/** URLs are short-lived presigned GETs for EXIF-free renditions; they are null until processing completes. */
public record MemoryMediaDto(
        int position,
        MediaType type,
        String displayUrl,
        String thumbUrl,
        Integer width,
        Integer height,
        String blurhash
) {
}
