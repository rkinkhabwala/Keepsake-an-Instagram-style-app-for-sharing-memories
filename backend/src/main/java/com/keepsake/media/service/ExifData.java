package com.keepsake.media.service;

import java.time.LocalDate;

/**
 * Metadata read from an original upload before it is discarded from derived images.
 *
 * @param orientation EXIF orientation 1–8; 1 when absent
 */
public record ExifData(LocalDate takenOn, Double latitude, Double longitude, int orientation) {

    public static final ExifData EMPTY = new ExifData(null, null, null, 1);
}
