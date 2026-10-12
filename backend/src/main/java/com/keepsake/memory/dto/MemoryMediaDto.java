package com.keepsake.memory.dto;

import com.keepsake.memory.entity.MediaType;

/**
 * URLs are short-lived presigned GETs for metadata-free renditions; they are null until processing completes. For
 * a video, {@code displayUrl} is the 720p MP4 and {@code posterUrl} its poster image.
 */
public record MemoryMediaDto(
        int position,
        MediaType type,
        String displayUrl,
        String thumbUrl,
        Integer width,
        Integer height,
        String blurhash,
        String posterUrl,
        Integer durationMs
) {
}
