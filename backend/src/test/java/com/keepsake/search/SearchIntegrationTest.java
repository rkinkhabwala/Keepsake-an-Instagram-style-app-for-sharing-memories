package com.keepsake.search;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SearchIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DataSource dataSource;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void findsUsersByUsernameOrDisplayNamePrefixCaseInsensitivelyExactMatchFirst() throws Exception {
        TestUser viewer = api.signup("srch_viewer");
        api.signup("srch_mila");
        api.signup("srch_mil");
        TestUser byName = api.signup("srch_zz_other");
        mockMvc.perform(as(byName, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Srch_Mil Lopez\"}"))
                .andExpect(status().isOk());

        assertThat(usernames(viewer, "SRCH_MIL")).containsExactly("srch_mil", "srch_mila", "srch_zz_other");
        assertThat(usernames(viewer, "@srch_mila")).containsExactly("srch_mila");
        assertThat(results(viewer, "#srch_mil").get("users")).isEmpty();
    }

    @Test
    void treatsLikeWildcardsLiterally() throws Exception {
        TestUser viewer = api.signup("srch_wild_viewer");
        api.signup("srchxwild");

        assertThat(usernames(viewer, "srch_wild")).containsExactly("srch_wild_viewer");
        assertThat(usernames(viewer, "srch%")).isEmpty();
        assertThat(usernames(viewer, "srch\\")).isEmpty();
    }

    @Test
    void findsHashtagsOnlyWhenTheViewerCanSeeAMemoryUsingThem() throws Exception {
        TestUser author = api.signup("srch_tag_author");
        TestUser stranger = api.signup("srch_tag_stranger");
        memory(author, "#srchtagpublic", "PUBLIC");
        memory(author, "#srchtagsecret", "ONLY_ME");

        assertThat(hashtags(stranger, "srchtag")).containsExactly("srchtagpublic");
        assertThat(hashtags(author, "#SrchTag")).containsExactly("srchtagpublic", "srchtagsecret");
        assertThat(results(author, "@srchtag").get("hashtags")).isEmpty();
    }

    @Test
    void hidesBlockedUsersInBothDirections() throws Exception {
        TestUser a = api.signup("srch_blk_a");
        TestUser b = api.signup("srch_blk_b");
        TestUser c = api.signup("srch_blk_c");
        memory(b, "#srchblkbtag", "PUBLIC");
        api.block(a, b);

        assertThat(usernames(a, "srch_blk")).containsExactly("srch_blk_a", "srch_blk_c");
        assertThat(usernames(b, "srch_blk")).containsExactly("srch_blk_b", "srch_blk_c");
        assertThat(usernames(c, "srch_blk")).containsExactly("srch_blk_a", "srch_blk_b", "srch_blk_c");
        assertThat(hashtags(a, "srchblkb")).isEmpty();
    }

    @Test
    void capsResultsAndIgnoresEmptyOrOverlongQueries() throws Exception {
        TestUser viewer = api.signup("srch_cap_viewer");
        for (int i = 0; i < 21; i++) {
            api.signup("srch_cap_" + (char) ('a' + i));
        }

        assertThat(usernames(viewer, "srch_cap_")).hasSize(20);
        assertThat(results(viewer, "   ").get("users")).isEmpty();
        assertThat(results(viewer, "#").get("hashtags")).isEmpty();
        assertThat(results(viewer, "x".repeat(101)).get("users")).isEmpty();
    }

    @Test
    void prefixQueriesCanUseThePlainIndexes() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> plans = jdbc.execute((java.sql.Connection connection) -> {
            List<String> lines = new ArrayList<>();
            try (var statement = connection.createStatement()) {
                // The test database is tiny, so ask the planner to show an index plan if one is possible.
                statement.execute("SET enable_seqscan = off");
                for (String sql : List.of(
                        "EXPLAIN SELECT 1 FROM users WHERE lower(username) LIKE 'srch\\_%' ESCAPE '\\'",
                        "EXPLAIN SELECT 1 FROM users WHERE lower(display_name) LIKE 'srch%' ESCAPE '\\'",
                        "EXPLAIN SELECT 1 FROM hashtags WHERE tag LIKE 'srch%' ESCAPE '\\'")) {
                    try (var rows = statement.executeQuery(sql)) {
                        StringBuilder plan = new StringBuilder();
                        while (rows.next()) {
                            plan.append(rows.getString(1)).append('\n');
                        }
                        lines.add(plan.toString());
                    }
                }
                statement.execute("RESET enable_seqscan");
            }
            return lines;
        });

        assertThat(plans.get(0)).contains("ix_users_username_prefix");
        assertThat(plans.get(1)).contains("ix_users_display_name_prefix");
        assertThat(plans.get(2)).contains("ix_hashtags_tag_prefix");
    }

    private void memory(TestUser author, String caption, String visibility) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("caption", caption, "visibility", visibility));
        api.awaitStatus(author, memoryId, "READY");
    }

    private JsonNode results(TestUser viewer, String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/search").param("q", query)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> usernames(TestUser viewer, String query) throws Exception {
        List<String> names = new ArrayList<>();
        results(viewer, query).get("users").forEach(user -> names.add(user.get("username").asText()));
        return names;
    }

    private List<String> hashtags(TestUser viewer, String query) throws Exception {
        List<String> tags = new ArrayList<>();
        results(viewer, query).get("hashtags").forEach(tag -> tags.add(tag.asText()));
        return tags;
    }
}
