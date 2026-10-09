package com.keepsake.media.entity;

import java.time.LocalDate;

/**
 * The outputs of processing one upload. For a photo, {@code displayKey} is the 1080px WebP and poster/duration are
 * null; for a video, {@code displayKey} is the 720p MP4 and {@code posterKey} its 1080px WebP poster.
 */
public record ProcessedMedia(
        String displayKey,
        String thumbKey,
        int width,
        int height,
        String blurhash,
        LocalDate takenOn,
        Double latitude,
        Double longitude,
        String posterKey,
        Integer durationMs
) {
}
