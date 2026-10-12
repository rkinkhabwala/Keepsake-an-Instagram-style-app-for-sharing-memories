package com.keepsake.feed;

import static com.keepsake.support.TestApi.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.service.MemoryListService;
import com.keepsake.support.TestApi.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;

/** Reads a whole feed page by page, from the API and from the fan-out-on-read reference query. */
record FeedTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, MemoryListService fanOutOnRead) {

    List<UUID> feedIds(TestUser viewer, int pageSize) throws Exception {
        List<UUID> ids = new ArrayList<>();
        String cursor = null;
        do {
            var request = get("/api/feed").param("limit", String.valueOf(pageSize));
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = objectMapper.readTree(mockMvc.perform(as(viewer, request))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            page.get("items").forEach(item -> ids.add(UUID.fromString(item.get("id").asText())));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
        } while (cursor != null);
        return ids;
    }

    List<UUID> referenceIds(UUID viewerId) {
        List<UUID> ids = new ArrayList<>();
        String cursor = null;
        do {
            CursorPage<MemoryDto> page = fanOutOnRead.feed(viewerId, CursorPageRequest.parse(cursor, 100));
            page.items().forEach(memory -> ids.add(memory.id()));
            cursor = page.nextCursor();
        } while (cursor != null);
        return ids;
    }
}
