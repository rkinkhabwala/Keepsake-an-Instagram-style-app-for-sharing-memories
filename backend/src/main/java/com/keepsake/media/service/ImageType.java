package com.keepsake.media.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Image formats accepted for upload, identified by declared content type and by magic bytes. */
public enum ImageType {
    JPEG(List.of("image/jpeg")),
    PNG(List.of("image/png")),
    WEBP(List.of("image/webp")),
    HEIC(List.of("image/heic", "image/heif"));

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private final List<String> contentTypes;

    ImageType(List<String> contentTypes) {
        this.contentTypes = contentTypes;
    }

    public static Optional<ImageType> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.contentTypes.contains(normalized)).findFirst();
    }

    /** Identifies the format from the file's leading bytes, ignoring any declared type or extension. */
    public static Optional<ImageType> detect(byte[] bytes) {
        if (startsWith(bytes, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(bytes, 4, 4).equals("ftyp") && HEIF_BRANDS.contains(ascii(bytes, 8, 4))) {
            return Optional.of(HEIC);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        if (bytes.length < offset + prefix.length) {
            return false;
        }
        return Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        if (bytes.length < offset + length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
