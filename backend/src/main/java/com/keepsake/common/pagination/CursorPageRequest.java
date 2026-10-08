package com.keepsake.common.pagination;

import java.util.UUID;

public record CursorPageRequest(UUID cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public static CursorPageRequest parse(String cursor, Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (cursor == null || cursor.isBlank()) {
            return new CursorPageRequest(null, resolvedLimit);
        }
        try {
            return new CursorPageRequest(UUID.fromString(cursor), resolvedLimit);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCursorException();
        }
    }

}
