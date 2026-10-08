package com.keepsake.common.pagination;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }

    public static <T> CursorPage<T> fromLookahead(
            List<T> rows,
            int limit,
            Function<T, UUID> idExtractor
    ) {
        if (limit < 1) {
            throw new InvalidRequestException("limit must be positive");
        }
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }

        List<T> items = rows.subList(0, limit);
        String nextCursor = idExtractor.apply(items.getLast()).toString();
        return new CursorPage<>(items, nextCursor);
    }
}
