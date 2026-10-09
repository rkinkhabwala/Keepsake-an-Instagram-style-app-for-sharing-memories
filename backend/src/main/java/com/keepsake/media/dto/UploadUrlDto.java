package com.keepsake.media.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Where and how the client uploads one file; {@code headers} must be sent with the PUT unchanged. */
public record UploadUrlDto(UUID uploadId, String url, String method, Map<String, String> headers, Instant expiresAt) {
}
