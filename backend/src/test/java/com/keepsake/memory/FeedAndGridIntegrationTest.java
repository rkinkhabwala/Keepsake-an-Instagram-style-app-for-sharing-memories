package com.keepsake.memory;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class FeedAndGridIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void feedShowsOwnAndFollowedMemoriesThatPassThePolicyNewestFirst() throws Exception {
        TestUser viewer = api.signup("feed_viewer");
        TestUser followed = api.signup("feed_followed");
        TestUser privateFollowed = api.signup("feed_private_followed");
        TestUser privatePending = api.signup("feed_private_pending");
        TestUser notFollowed = api.signup("feed_not_followed");
        TestUser closeFriendOf = api.signup("feed_cf_author");
        api.makePrivate(privateFollowed);
        api.makePrivate(privatePending);
        api.follow(viewer, followed);
        api.follow(viewer, privateFollowed);
        api.requestFollow(viewer, privatePending);
        api.follow(viewer, closeFriendOf);
        api.addCloseFriend(closeFriendOf, viewer);

        UUID ownOnlyMe = api.createReadyMemory(viewer, "ONLY_ME");
        UUID followedPublic = api.createReadyMemory(followed, "PUBLIC");
        UUID followedFollowers = api.createReadyMemory(followed, "FOLLOWERS");
        UUID followedCloseFriends = api.createReadyMemory(followed, "CLOSE_FRIENDS");
        UUID followedOnlyMe = api.createReadyMemory(followed, "ONLY_ME");
        UUID privateFollowers = api.createReadyMemory(privateFollowed, "FOLLOWERS");
        UUID pendingPublic = api.createReadyMemory(privatePending, "PUBLIC");
        UUID strangerPublic = api.createReadyMemory(notFollowed, "PUBLIC");
        UUID closeFriendsMemory = api.createReadyMemory(closeFriendOf, "CLOSE_FRIENDS");
        UUID deleted = api.createReadyMemory(followed, "PUBLIC");
        mockMvc.perform(as(followed, delete("/api/memories/" + deleted))).andExpect(status().isNoContent());
        UUID processing = api.createMemory(followed,
                List.of(api.requestUpload(followed, "image/jpeg", 100)), java.util.Map.of());

        List<String> feed = feedIds(viewer, 50);

        assertThat(feed).containsExactly(
                closeFriendsMemory.toString(),
                privateFollowers.toString(),
                followedFollowers.toString(),
                followedPublic.toString(),
                ownOnlyMe.toString());
        assertThat(feed).doesNotContain(followedCloseFriends.toString(), followedOnlyMe.toString(),
                pendingPublic.toString(), strangerPublic.toString(), deleted.toString(), processing.toString());
    }

    @Test
    void feedPaginatesWithCursorAndReflectsUnfollowImmediately() throws Exception {
        TestUser viewer = api.signup("feed_page_viewer");
        TestUser author = api.signup("feed_page_author");
        api.follow(viewer, author);
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.addFirst(api.createReadyMemory(author, "PUBLIC").toString());
        }

        JsonNode first = feedPage(viewer, null, 2);
        JsonNode second = feedPage(viewer, first.get("nextCursor").asText(), 2);
        JsonNode third = feedPage(viewer, second.get("nextCursor").asText(), 2);
        List<String> paged = new ArrayList<>();
        for (JsonNode page : List.of(first, second, third)) {
            page.get("items").forEach(item -> paged.add(item.get("id").asText()));
        }
        assertThat(paged).containsExactlyElementsOf(created);
        assertThat(third.get("nextCursor").isNull()).isTrue();
        assertThat(first.at("/items/0/author/username").asText()).isEqualTo("feed_page_author");
        assertThat(first.at("/items/0/media/0/thumbUrl").asText()).contains("thumb/");

        mockMvc.perform(as(viewer, delete("/api/users/feed_page_author/follow"))).andExpect(status().isNoContent());
        assertThat(feedIds(viewer, 20)).isEmpty();

        mockMvc.perform(as(viewer, get("/api/feed?cursor=not-a-uuid"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(viewer, get("/api/feed?limit=101"))).andExpect(status().isBadRequest());
    }

    @Test
    void cursorsNeverRevealWhetherAHiddenMemoryExists() throws Exception {
        TestUser viewer = api.signup("feed_oracle_viewer");
        TestUser stranger = api.signup("feed_oracle_stranger");
        UUID hidden = api.createReadyMemory(stranger, "ONLY_ME");

        // A hidden memory's id and a made-up id are both just positions: same status, same (empty) result.
        for (String cursor : List.of(hidden.toString(), UUID.randomUUID().toString())) {
            mockMvc.perform(as(viewer, get("/api/feed?cursor=" + cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty());
            mockMvc.perform(as(viewer, get("/api/users/feed_oracle_stranger/memories?cursor=" + cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @Test
    void gridShowsOnlyMemoriesTheViewerMaySeeAsThumbs() throws Exception {
        TestUser author = api.signup("grid_author");
        TestUser follower = api.signup("grid_follower");
        TestUser closeFriend = api.signup("grid_close");
        TestUser stranger = api.signup("grid_stranger");
        api.follow(follower, author);
        api.addCloseFriend(author, closeFriend);
        UUID publicMemory = api.createReadyMemory(author, "PUBLIC");
        UUID followersMemory = api.createReadyMemory(author, "FOLLOWERS");
        UUID closeFriendsMemory = api.createReadyMemory(author, "CLOSE_FRIENDS");
        UUID onlyMe = api.createReadyMemory(author, "ONLY_ME");

        assertThat(gridIds(stranger, "grid_author")).containsExactly(publicMemory.toString());
        assertThat(gridIds(follower, "grid_author"))
                .containsExactly(followersMemory.toString(), publicMemory.toString());
        assertThat(gridIds(closeFriend, "grid_author"))
                .containsExactly(closeFriendsMemory.toString(), publicMemory.toString());
        assertThat(gridIds(author, "grid_author")).containsExactly(onlyMe.toString(),
                closeFriendsMemory.toString(), followersMemory.toString(), publicMemory.toString());

        mockMvc.perform(as(stranger, get("/api/users/grid_author/memories")))
                .andExpect(jsonPath("$.items[0].thumbUrl").value(org.hamcrest.Matchers.containsString("thumb/")))
                .andExpect(jsonPath("$.items[0].blurhash").isString())
                .andExpect(jsonPath("$.items[0].mediaCount").value(1))
                .andExpect(jsonPath("$.items[0].caption").doesNotExist());
        // Counts only include memories the viewer could open.
        mockMvc.perform(as(stranger, get("/api/users/grid_author"))).andExpect(jsonPath("$.memoryCount").value(1));
        mockMvc.perform(as(author, get("/api/users/grid_author"))).andExpect(jsonPath("$.memoryCount").value(4));
    }

    @Test
    void privateAccountGridIsEmptyForNonFollowersUntilApproved() throws Exception {
        TestUser author = api.signup("grid_priv_author");
        TestUser requester = api.signup("grid_priv_requester");
        api.makePrivate(author);
        UUID memory = api.createReadyMemory(author, "PUBLIC");
        api.requestFollow(requester, author);

        assertThat(gridIds(requester, "grid_priv_author")).isEmpty();
        mockMvc.perform(as(author, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/follow-requests/" + requester.id() + "/accept")))
                .andExpect(status().isNoContent());
        assertThat(gridIds(requester, "grid_priv_author")).containsExactly(memory.toString());

        // Turning the account public exposes PUBLIC memories to everyone.
        TestUser stranger = api.signup("grid_priv_stranger");
        assertThat(gridIds(stranger, "grid_priv_author")).isEmpty();
        mockMvc.perform(as(author, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":false}"))
                .andExpect(status().isOk());
        assertThat(gridIds(stranger, "grid_priv_author")).containsExactly(memory.toString());
        mockMvc.perform(as(stranger, get("/api/users/nobody_here_grid/memories"))).andExpect(status().isNotFound());
    }

    private List<String> feedIds(TestUser viewer, int limit) throws Exception {
        List<String> ids = new ArrayList<>();
        feedPage(viewer, null, limit).get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private JsonNode feedPage(TestUser viewer, String cursor, int limit) throws Exception {
        String query = "/api/feed?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor);
        return objectMapper.readTree(mockMvc.perform(as(viewer, get(query)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> gridIds(TestUser viewer, String username) throws Exception {
        JsonNode page = objectMapper.readTree(mockMvc.perform(
                        as(viewer, get("/api/users/" + username + "/memories?limit=50")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
