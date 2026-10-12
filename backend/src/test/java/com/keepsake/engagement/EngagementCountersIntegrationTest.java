package com.keepsake.engagement;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.engagement.service.EngagementCounters;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Spec §7: counters change in Redis right away and reach Postgres on the periodic flush. */
class EngagementCountersIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EngagementCounters counters;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
        counters.flush();
    }

    @Test
    void countsShowImmediatelyAndReachPostgresOnFlush() throws Exception {
        TestUser author = api.signup("cnt_author");
        TestUser fan = api.signup("cnt_fan");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");

        mockMvc.perform(as(fan, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(author, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        comment(fan, memoryId);

        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isEqualTo(2);
        assertThat(api.getMemory(author, memoryId).get("commentCount").asInt()).isEqualTo(1);
        assertThat(stored(memoryId)).containsExactly(0, 0);

        counters.flush();

        assertThat(stored(memoryId)).containsExactly(2, 1);
        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isEqualTo(2);
        assertThat(redis.opsForHash().get("keepsake:counts:likes", memoryId.toString())).isNull();
    }

    @Test
    void unlikesAndDeletedCommentsCountDown() throws Exception {
        TestUser author = api.signup("cnt_down");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        mockMvc.perform(as(author, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        UUID commentId = comment(author, memoryId);
        counters.flush();

        mockMvc.perform(as(author, delete("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(author, delete("/api/comments/" + commentId))).andExpect(status().isNoContent());

        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isZero();
        assertThat(api.getMemory(author, memoryId).get("commentCount").asInt()).isZero();
        counters.flush();
        assertThat(stored(memoryId)).containsExactly(0, 0);
    }

    @Test
    void aFlushInterruptedAfterItsSnapshotIsFinishedByTheNextOneWithoutLosingNewChanges() throws Exception {
        TestUser author = api.signup("cnt_crash");
        TestUser fan = api.signup("cnt_crash_fan");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        mockMvc.perform(as(author, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());

        // Simulate a flush that took its snapshot and then died before updating Postgres.
        for (String key : List.of("keepsake:counts:likes", "keepsake:counts:dirty")) {
            redis.rename(key, key + ":flushing");
        }
        mockMvc.perform(as(fan, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isEqualTo(2);

        counters.flush();
        assertThat(stored(memoryId)).containsExactly(2, 0);
        counters.flush();
        assertThat(stored(memoryId)).containsExactly(2, 0);
        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isEqualTo(2);
        assertThat(redis.hasKey("keepsake:counts:dirty:flushing")).isFalse();
    }

    @Test
    void reconcileNowSetsExactCountsFromTheRows() throws Exception {
        TestUser author = api.signup("cnt_reconcile");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        mockMvc.perform(as(author, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        jdbc.update("UPDATE memories SET like_count = 42 WHERE id = ?", memoryId);

        counters.reconcileNow(List.of(memoryId));

        assertThat(stored(memoryId)).containsExactly(1, 0);
        assertThat(api.getMemory(author, memoryId).get("likeCount").asInt()).isEqualTo(1);
    }

    private List<Integer> stored(UUID memoryId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT like_count, comment_count FROM memories WHERE id = ?", memoryId);
        return List.of((Integer) row.get("like_count"), (Integer) row.get("comment_count"));
    }

    private UUID comment(TestUser user, UUID memoryId) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"nice\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}
