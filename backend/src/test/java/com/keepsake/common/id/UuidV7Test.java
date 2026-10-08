package com.keepsake.common.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generatesTimeOrderedVersionSevenIds() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ids.add(UuidV7.generate());
        }

        assertTrue(ids.stream().allMatch(id -> id.version() == 7));
        assertTrue(ids.stream().allMatch(id -> id.variant() == 2));
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1).compareTo(ids.get(i)) < 0);
        }
        long encodedTimestamp = ids.getLast().getMostSignificantBits() >>> 16;
        assertTrue(Math.abs(System.currentTimeMillis() - encodedTimestamp) < 5_000);
        assertEquals(ids.size(), ids.stream().distinct().count());
    }
}
