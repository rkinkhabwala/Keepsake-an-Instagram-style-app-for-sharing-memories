package com.keepsake.common.pagination;

import java.util.UUID;

public record CursorPageRequest(UUID cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final UUID MAX_UUID = new UUID(-1L, -1L);

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

    /** The cursor for a {@code id < :cursor ORDER BY id DESC} query; the first page starts above every id. */
    public UUID cursorOrMax() {
        return cursor == null ? MAX_UUID : cursor;
    }

    /** One extra row so {@link CursorPage#fromLookahead} can tell whether another page exists. */
    public org.springframework.data.domain.Limit lookaheadLimit() {
        return org.springframework.data.domain.Limit.of(limit + 1);
    }
}
