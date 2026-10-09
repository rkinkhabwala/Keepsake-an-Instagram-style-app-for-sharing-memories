package com.keepsake.media.entity;

import java.time.LocalDate;

/** The outputs of processing one image upload. */
public record ProcessedImage(
        String displayKey,
        String thumbKey,
        int width,
        int height,
        String blurhash,
        LocalDate takenOn,
        Double latitude,
        Double longitude
) {
}
