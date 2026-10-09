package com.keepsake.media.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Video formats accepted for upload. MP4 and QuickTime (MOV) are both ISO base media files, so either declared
 * type may hold either container; the magic-byte check is there to reject files that are not video at all.
 */
public enum VideoType {
    MP4("video/mp4"),
    MOV("video/quicktime");

    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "mmp4", "MSNV", "3gp4",
            "3gp5", "3g2a");
    /** Atoms a QuickTime file without an ftyp box can start with. */
    private static final Set<String> QUICKTIME_ATOMS = Set.of("moov", "mdat", "wide", "free", "skip", "pnot");

    private final String contentType;

    VideoType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<VideoType> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        for (VideoType type : values()) {
            if (type.contentType.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Identifies the container from the file's leading bytes, ignoring any declared type. */
    public static Optional<VideoType> detect(byte[] bytes) {
        String box = ascii(bytes, 4, 4);
        if (box.equals("ftyp")) {
            String brand = ascii(bytes, 8, 4);
            if (brand.equals("qt  ")) {
                return Optional.of(MOV);
            }
            return MP4_BRANDS.contains(brand) ? Optional.of(MP4) : Optional.empty();
        }
        return QUICKTIME_ATOMS.contains(box) ? Optional.of(MOV) : Optional.empty();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return bytes.length < offset + length ? "" : new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }
}
