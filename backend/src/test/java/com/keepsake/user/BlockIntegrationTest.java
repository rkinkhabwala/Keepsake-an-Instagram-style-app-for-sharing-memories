package com.keepsake.user;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Each effect of a block between two users, in both directions, and that unblocking restores none of it. */
class BlockIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void blockRemovesFollowsInBothDirectionsAndUnblockDoesNotRestoreThem() throws Exception {
        TestUser a = api.signup("blk_follow_a");
        TestUser b = api.signup("blk_follow_b");
        api.follow(a, b);
        api.follow(b, a);

        api.block(a, b);
        mockMvc.perform(as(a, delete("/api/blocks/" + b.id()))).andExpect(status().isNoContent());

        expectRelationship(a, b, "NONE");
        expectRelationship(b, a, "NONE");
        mockMvc.perform(as(a, get("/api/users/" + a.username()))).andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0));
    }

    @Test
    void blockCancelsPendingRequestsInBothDirections() throws Exception {
        TestUser a = api.signup("blk_pending_a");
        TestUser b = api.signup("blk_pending_b");
        api.makePrivate(a);
        api.makePrivate(b);
        api.requestFollow(a, b);
        api.requestFollow(b, a);

        api.block(b, a);
        mockMvc.perform(as(b, delete("/api/blocks/" + a.id()))).andExpect(status().isNoContent());

        mockMvc.perform(as(a, get("/api/follow-requests"))).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(as(b, get("/api/follow-requests"))).andExpect(jsonPath("$.items").isEmpty());
        expectRelationship(a, b, "NONE");
        expectRelationship(b, a, "NONE");
    }

    @Test
    void blockRemovesCloseFriendEntriesBothWays() throws Exception {
        TestUser a = api.signup("blk_cf_a");
        TestUser b = api.signup("blk_cf_b");
        TestUser c = api.signup("blk_cf_c");
        api.addCloseFriend(a, b);
        api.addCloseFriend(b, a);
        api.addCloseFriend(a, c);

        api.block(a, b);
        mockMvc.perform(as(a, delete("/api/blocks/" + b.id()))).andExpect(status().isNoContent());

        mockMvc.perform(as(a, get("/api/close-friends")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("blk_cf_c")));
        mockMvc.perform(as(b, get("/api/close-friends"))).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void blockRemovesTagsBetweenThemOnly() throws Exception {
        TestUser a = api.signup("blk_tag_a");
        TestUser b = api.signup("blk_tag_b");
        TestUser c = api.signup("blk_tag_c");
        UUID aTagsB = api.createReadyMemory(a, "PUBLIC", List.of(b.username(), c.username()));
        UUID bTagsA = api.createReadyMemory(b, "PUBLIC", List.of(a.username()));

        api.block(b, a);
        mockMvc.perform(as(b, delete("/api/blocks/" + a.id()))).andExpect(status().isNoContent());

        assertThat(taggedUsernames(c, aTagsB)).containsExactly("blk_tag_c");
        assertThat(taggedUsernames(c, bTagsA)).isEmpty();
    }

    @Test
    void blockedUsersCannotSeeOrReachEachOther() throws Exception {
        TestUser a = api.signup("blk_reach_a");
        TestUser b = api.signup("blk_reach_b");
        UUID aMemory = api.createReadyMemory(a, "PUBLIC");
        UUID bMemory = api.createReadyMemory(b, "PUBLIC");
        api.block(a, b);

        // The blocked side and the blocking side get the same answers: it is as if the other did not exist.
        for (TestUser[] pair : new TestUser[][] {{a, b}, {b, a}}) {
            TestUser viewer = pair[0];
            TestUser other = pair[1];
            UUID othersMemory = other == a ? aMemory : bMemory;
            mockMvc.perform(as(viewer, get("/api/users/" + other.username()))).andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, get("/api/users/" + other.username() + "/memories")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, post("/api/users/" + other.username() + "/follow")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, put("/api/close-friends/" + other.id()))).andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, get("/api/memories/" + othersMemory))).andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, post("/api/memories/" + othersMemory + "/comments"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\":\"hi\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(as(viewer, post("/api/memories"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(Map.of(
                                    "visibility", "PUBLIC",
                                    "uploadIds", List.of(api.upload(viewer, "image/jpeg", TestImages.jpeg(8, 8))),
                                    "taggedUsernames", List.of(other.username())))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Unknown user @" + other.username()));
        }
    }

    @Test
    void commentsAreHiddenBothWaysEvenOnAThirdPartysMemory() throws Exception {
        TestUser a = api.signup("blk_cmt_a");
        TestUser b = api.signup("blk_cmt_b");
        TestUser host = api.signup("blk_cmt_host");
        UUID memory = api.createReadyMemory(host, "PUBLIC");
        UUID fromA = comment(a, memory, "from a", null);
        comment(b, memory, "b replies to a", fromA);
        comment(b, memory, "from b", null);
        comment(host, memory, "from host", null);

        api.block(a, b);

        assertThat(threadBodies(a, memory)).containsExactly("from a", "from host");
        assertThat(threadBodies(b, memory)).containsExactly("from b", "from host");
        assertThat(threadBodies(host, memory)).containsExactly("from a", "b replies to a", "from b", "from host");
        assertThat(previewBodies(a, memory)).containsExactly("from a", "from host");
        assertThat(previewBodies(b, memory)).containsExactly("from b", "from host");
    }

    @Test
    void eachIsHiddenFromTheOtherInFollowerAndFollowingLists() throws Exception {
        TestUser a = api.signup("blk_list_a");
        TestUser b = api.signup("blk_list_b");
        TestUser hub = api.signup("blk_list_hub");
        TestUser bystander = api.signup("blk_list_by");
        api.follow(a, hub);
        api.follow(b, hub);
        api.follow(hub, a);
        api.follow(hub, b);

        api.block(b, a);

        mockMvc.perform(as(a, get("/api/users/blk_list_hub/followers")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("blk_list_a")));
        mockMvc.perform(as(b, get("/api/users/blk_list_hub/following")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("blk_list_b")));
        mockMvc.perform(as(bystander, get("/api/users/blk_list_hub/followers")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.containsInAnyOrder("blk_list_a", "blk_list_b")));
    }

    @Test
    void memoriesStayHiddenUntilUnblockedAndThenFollowTheNormalRules() throws Exception {
        TestUser a = api.signup("blk_feed_a");
        TestUser b = api.signup("blk_feed_b");
        api.follow(a, b);
        UUID bPublic = api.createReadyMemory(b, "PUBLIC");
        UUID bFollowers = api.createReadyMemory(b, "FOLLOWERS");

        api.block(a, b);
        assertThat(feedIds(a)).doesNotContain(bPublic.toString(), bFollowers.toString());

        mockMvc.perform(as(a, delete("/api/blocks/" + b.id()))).andExpect(status().isNoContent());
        // The follow was removed by the block, so only the PUBLIC memory comes back, and only on b's profile.
        mockMvc.perform(as(a, get("/api/memories/" + bPublic))).andExpect(status().isOk());
        mockMvc.perform(as(a, get("/api/memories/" + bFollowers))).andExpect(status().isNotFound());
        assertThat(feedIds(a)).doesNotContain(bPublic.toString());
    }

    @Test
    void blockEndpointsListValidateAndAreIdempotent() throws Exception {
        TestUser a = api.signup("blk_api_a");
        TestUser b = api.signup("blk_api_b");
        TestUser c = api.signup("blk_api_c");

        api.block(a, b);
        api.block(a, b);
        api.block(a, c);
        api.block(c, a);

        mockMvc.perform(as(a, get("/api/blocks")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("blk_api_c", "blk_api_b")));
        // Who has blocked you is never listed.
        mockMvc.perform(as(b, get("/api/blocks"))).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(as(a, put("/api/blocks/" + a.id()))).andExpect(status().isBadRequest());
        mockMvc.perform(as(a, put("/api/blocks/" + UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(as(a, delete("/api/blocks/" + UUID.randomUUID()))).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/blocks/" + b.id())).andExpect(status().isUnauthorized());
    }

    private void expectRelationship(TestUser viewer, TestUser other, String relationship) throws Exception {
        mockMvc.perform(as(viewer, get("/api/users/" + other.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship").value(relationship));
    }

    private List<String> taggedUsernames(TestUser viewer, UUID memoryId) throws Exception {
        List<String> names = new ArrayList<>();
        api.getMemory(viewer, memoryId).get("taggedPeople").forEach(person -> names.add(person.get("username").asText()));
        return names;
    }

    private UUID comment(TestUser user, UUID memoryId, String body, UUID parentId) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(Map.of("body", body));
        if (parentId != null) {
            request.put("parentId", parentId);
        }
        String response = mockMvc.perform(as(user, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    /** Every comment body the viewer sees in the thread, replies right after their parent. */
    private List<String> threadBodies(TestUser viewer, UUID memoryId) throws Exception {
        JsonNode page = objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/memories/" + memoryId + "/comments")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> bodies = new ArrayList<>();
        page.get("items").forEach(comment -> {
            bodies.add(comment.get("body").asText());
            comment.get("replies").forEach(reply -> bodies.add(reply.get("body").asText()));
        });
        return bodies;
    }

    private List<String> previewBodies(TestUser viewer, UUID memoryId) throws Exception {
        List<String> bodies = new ArrayList<>();
        api.getMemory(viewer, memoryId).get("latestComments").forEach(comment -> bodies.add(comment.get("body").asText()));
        return bodies;
    }

    private List<String> feedIds(TestUser viewer) throws Exception {
        JsonNode page = objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/feed?limit=50")))
                .andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
