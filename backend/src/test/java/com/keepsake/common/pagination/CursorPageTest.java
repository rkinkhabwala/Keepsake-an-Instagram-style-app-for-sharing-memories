package com.keepsake.common.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorPageTest {

    private record Item(UUID id, String value) {
    }

    @Test
    void returnsOnlyRequestedItemsAndTheLastReturnedIdWhenMoreExist() {
        List<Item> rows = List.of(
                new Item(UUID.randomUUID(), "first"),
                new Item(UUID.randomUUID(), "second"),
                new Item(UUID.randomUUID(), "lookahead")
        );

        CursorPage<Item> page = CursorPage.fromLookahead(rows, 2, Item::id);

        assertEquals(rows.subList(0, 2), page.items());
        assertEquals(rows.get(1).id().toString(), page.nextCursor());
    }

    @Test
    void omitsNextCursorWhenThereAreNoMoreItems() {
        CursorPage<Item> page = CursorPage.fromLookahead(
                List.of(new Item(UUID.randomUUID(), "only")), 2, Item::id);

        assertEquals(1, page.items().size());
        assertNull(page.nextCursor());
    }

    @Test
    void parsesDefaultAndProvidedLimitsAndRejectsInvalidInput() {
        assertEquals(CursorPageRequest.DEFAULT_LIMIT, CursorPageRequest.parse(null, null).limit());
        assertEquals(35, CursorPageRequest.parse(UUID.randomUUID().toString(), 35).limit());
        assertThrows(InvalidCursorException.class, () -> CursorPageRequest.parse("bad", null));
        assertThrows(InvalidRequestException.class, () -> CursorPageRequest.parse(null, 0));
        assertThrows(InvalidRequestException.class,
                () -> CursorPageRequest.parse(null, CursorPageRequest.MAX_LIMIT + 1));
    }
}
