package com.keepsake.hashtag;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import db.migration.V9_1__Backfill_hashtags;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class HashtagIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void tagPageListsOnlyMemoriesTheViewerMaySee() throws Exception {
        TestUser author = api.signup("tag_vis_author");
        TestUser follower = api.signup("tag_vis_follower");
        TestUser stranger = api.signup("tag_vis_stranger");
        TestUser blocked = api.signup("tag_vis_blocked");
        api.follow(follower, author);
        UUID publicMemory = memory(author, "Sunset #TagVisSunset", "PUBLIC");
        UUID followersMemory = memory(author, "Again #tagvissunset", "FOLLOWERS");
        UUID deleted = memory(author, "#tagvissunset gone", "PUBLIC");
        mockMvc.perform(as(author, delete("/api/memories/" + deleted))).andExpect(status().isNoContent());
        api.block(author, blocked);

        assertThat(tagIds(stranger, "TagVisSunset")).containsExactly(publicMemory.toString());
        // A leading # (sent as %23 by browsers) is accepted.
        assertThat(tagIds(follower, "#tagvissunset"))
                .containsExactly(followersMemory.toString(), publicMemory.toString());
        assertThat(tagIds(blocked, "tagvissunset")).isEmpty();
    }

    @Test
    void privateAccountsTagsAreHiddenFromNonFollowers() throws Exception {
        TestUser author = api.signup("tag_priv_author");
        TestUser stranger = api.signup("tag_priv_stranger");
        api.makePrivate(author);
        memory(author, "#tagprivsecret", "PUBLIC");

        assertThat(tagIds(stranger, "tagprivsecret")).isEmpty();
        assertThat(tagIds(author, "tagprivsecret")).hasSize(1);
    }

    @Test
    void editingTheCaptionReindexesTags() throws Exception {
        TestUser author = api.signup("tag_edit_author");
        UUID memoryId = memory(author, "#tageditbefore and #tageditkept", "PUBLIC");

        mockMvc.perform(as(author, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"#tageditkept and #tageditafter\"}"))
                .andExpect(status().isOk());

        assertThat(tagIds(author, "tageditbefore")).isEmpty();
        assertThat(tagIds(author, "tageditkept")).containsExactly(memoryId.toString());
        assertThat(tagIds(author, "tageditafter")).containsExactly(memoryId.toString());

        mockMvc.perform(as(author, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":null}"))
                .andExpect(status().isOk());
        assertThat(tagIds(author, "tageditkept")).isEmpty();
    }

    @Test
    void tagPagesAreCursorPaginatedAndValidated() throws Exception {
        TestUser author = api.signup("tag_page_author");
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            created.addFirst(memory(author, "#tagpaged " + i, "PUBLIC").toString());
        }

        JsonNode first = page(author, "/api/hashtags/tagpaged/memories?limit=2");
        JsonNode second = page(author, "/api/hashtags/tagpaged/memories?limit=2&cursor=" + first.get("nextCursor").asText());
        List<String> ids = new ArrayList<>();
        first.get("items").forEach(item -> ids.add(item.get("id").asText()));
        second.get("items").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).containsExactlyElementsOf(created);
        assertThat(second.get("nextCursor").isNull()).isTrue();

        assertThat(tagIds(author, "tagnobodyused")).isEmpty();
        mockMvc.perform(as(author, get("/api/hashtags/{tag}/memories", "two words"))).andExpect(status().isBadRequest());
    }

    @Test
    void backfillIndexesCaptionsWrittenBeforeHashtagsExisted() throws Exception {
        TestUser author = api.signup("tag_backfill_author");
        UUID memoryId = memory(author, "no tags yet", "PUBLIC");
        // As if written before V9: a caption with tags but no hashtag rows.
        jdbc.update("UPDATE memories SET caption = ? WHERE id = ?", "Old #TagBackfillOld post", memoryId);
        assertThat(tagIds(author, "tagbackfillold")).isEmpty();

        try (Connection connection = dataSource.getConnection()) {
            new V9_1__Backfill_hashtags().migrate(new Context() {
                @Override
                public Configuration getConfiguration() {
                    return null;
                }

                @Override
                public Connection getConnection() {
                    return connection;
                }
            });
        }

        assertThat(tagIds(author, "tagbackfillold")).containsExactly(memoryId.toString());
    }

    private UUID memory(TestUser author, String caption, String visibility) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("caption", caption, "visibility", visibility));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId;
    }

    private List<String> tagIds(TestUser viewer, String tag) throws Exception {
        List<String> ids = new ArrayList<>();
        objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/hashtags/{tag}/memories?limit=50", tag)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private JsonNode page(TestUser viewer, String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(viewer, get(path)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
