package com.keepsake.feed;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.feed.service.FeedFanOut;
import com.keepsake.memory.service.MemoryListService;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The fan-out feed must list exactly what the fan-out-on-read reference query lists, in the same order, through
 * random follows, requests, unfollows, privacy, visibility and close-friend changes, blocks, new and deleted
 * memories, celebrity changes, and dropped feeds.
 */
class FeedParityIntegrationTest extends IntegrationTestBase {

    private static final List<String> VISIBILITIES = List.of("PUBLIC", "FOLLOWERS", "CLOSE_FRIENDS", "ONLY_ME");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired FeedFanOut fanOut;
    @Autowired MemoryListService fanOutOnRead;

    @Test
    void fanOutFeedMatchesTheFanOutOnReadReference() throws Exception {
        TestApi api = new TestApi(mockMvc, objectMapper);
        FeedTestSupport feeds = new FeedTestSupport(mockMvc, objectMapper, fanOutOnRead);
        long seed = 20261009L;
        Random random = new Random(seed);
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            users.add(api.signup("parity_u" + i));
        }
        api.makePrivate(users.get(1));
        api.makePrivate(users.get(4));
        Map<UUID, TestUser> memories = new LinkedHashMap<>();
        for (TestUser user : users) {
            for (int i = 0; i < 2; i++) {
                memories.put(api.createReadyMemory(user, pick(random, VISIBILITIES)), user);
            }
        }

        for (int step = 1; step <= 100; step++) {
            TestUser a = pick(random, users);
            TestUser b = pick(random, users);
            switch (random.nextInt(16)) {
                case 0, 1, 2 -> attempt(a, post("/api/users/" + b.username() + "/follow"));
                case 3 -> attempt(a, delete("/api/users/" + b.username() + "/follow"));
                case 4 -> attempt(a, post("/api/follow-requests/" + b.id() + "/accept"));
                case 5 -> attempt(a, patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":" + random.nextBoolean() + "}"));
                case 6, 7, 8 -> {
                    UUID memoryId = pick(random, new ArrayList<>(memories.keySet()));
                    attempt(memories.get(memoryId), patch("/api/memories/" + memoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"visibility\":\"" + pick(random, VISIBILITIES) + "\"}"));
                }
                case 9 -> attempt(a, put("/api/close-friends/" + b.id()));
                case 10, 11 -> attempt(a, delete("/api/close-friends/" + b.id()));
                case 12 -> attempt(a, random.nextInt(3) == 0 ? put("/api/blocks/" + b.id())
                        : delete("/api/blocks/" + b.id()));
                case 13 -> memories.put(api.createReadyMemory(a, pick(random, VISIBILITIES)), a);
                case 14 -> {
                    UUID memoryId = pick(random, new ArrayList<>(memories.keySet()));
                    attempt(memories.get(memoryId), delete("/api/memories/" + memoryId));
                }
                default -> redis.delete("feed:" + a.id());
            }
            if (step % 2 == 0) {
                fanOut.refreshCelebrities();
                for (TestUser viewer : users) {
                    assertThat(feeds.feedIds(viewer, 1 + random.nextInt(4)))
                            .as("feed of %s at step %d (seed %d)", viewer.username(), step, seed)
                            .containsExactlyElementsOf(feeds.referenceIds(viewer.id()));
                }
            }
        }
    }

    /** Some random actions are not allowed (e.g. following someone who blocked you); their outcome is ignored. */
    private void attempt(TestUser user, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(as(user, request));
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
