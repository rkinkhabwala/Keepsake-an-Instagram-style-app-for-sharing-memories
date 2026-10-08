package com.keepsake.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class UserFollowIntegrationTest extends IntegrationTestBase {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void meReturnsAndUpdatesOwnProfile() throws Exception {
        TestUser user = signup("uf_me");

        mockMvc.perform(as(user, get("/api/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("uf_me"))
                .andExpect(jsonPath("$.isPrivate").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"  New Name  ","bio":" Hello ","isPrivate":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.bio").value("Hello"))
                .andExpect(jsonPath("$.isPrivate").value(true));

        // Omitted fields stay unchanged; an empty bio clears it.
        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.isPrivate").value(true));
    }

    @Test
    void updateMeRejectsInvalidFields() throws Exception {
        TestUser user = signup("uf_invalid");

        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bio", "x".repeat(151)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bio").exists());

        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users/someone/follow")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/close-friends")).andExpect(status().isUnauthorized());
    }

    @Test
    void followingPublicAccountIsImmediateAndIdempotent() throws Exception {
        TestUser alice = signup("uf_pub_alice");
        TestUser bob = signup("uf_pub_bob");

        follow(bob, "uf_pub_alice", "FOLLOWING");
        follow(bob, "uf_pub_alice", "FOLLOWING");

        mockMvc.perform(as(bob, get("/api/users/uf_pub_alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship").value("FOLLOWING"))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(0));
        mockMvc.perform(as(alice, get("/api/users/uf_pub_alice")))
                .andExpect(jsonPath("$.relationship").value("SELF"));
        mockMvc.perform(as(alice, get("/api/users/uf_pub_bob")))
                .andExpect(jsonPath("$.relationship").value("NONE"))
                .andExpect(jsonPath("$.followingCount").value(1));
        mockMvc.perform(as(alice, get("/api/users/uf_pub_alice/followers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_pub_bob")))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(as(alice, get("/api/users/uf_pub_bob/following")))
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_pub_alice")));

        mockMvc.perform(as(bob, delete("/api/users/uf_pub_alice/follow")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(bob, get("/api/users/uf_pub_alice")))
                .andExpect(jsonPath("$.relationship").value("NONE"))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    void followingPrivateAccountCreatesPendingRequestUntilAccepted() throws Exception {
        TestUser owner = signup("uf_priv_owner");
        makePrivate(owner);
        TestUser requester = signup("uf_priv_req");

        follow(requester, "uf_priv_owner", "REQUESTED");

        // A pending request grants nothing: the private account's connections stay hidden and uncounted.
        mockMvc.perform(as(requester, get("/api/users/uf_priv_owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.relationship").value("REQUESTED"))
                .andExpect(jsonPath("$.followerCount").value(0));
        mockMvc.perform(as(requester, get("/api/users/uf_priv_owner/followers")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(requester, get("/api/users/uf_priv_owner/following")))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(owner, get("/api/follow-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_priv_req")));
        mockMvc.perform(as(owner, get("/api/users/uf_priv_owner/followers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(as(owner, post("/api/follow-requests/" + requester.id() + "/accept")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, post("/api/follow-requests/" + requester.id() + "/accept")))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(requester, get("/api/users/uf_priv_owner")))
                .andExpect(jsonPath("$.relationship").value("FOLLOWING"))
                .andExpect(jsonPath("$.followerCount").value(1));
        mockMvc.perform(as(requester, get("/api/users/uf_priv_owner/followers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_priv_req")));
        mockMvc.perform(as(owner, get("/api/follow-requests")))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void decliningOrCancellingRequestRemovesIt() throws Exception {
        TestUser owner = signup("uf_decl_owner");
        makePrivate(owner);
        TestUser declined = signup("uf_decl_a");
        TestUser cancelled = signup("uf_decl_b");
        TestUser stranger = signup("uf_decl_c");

        follow(declined, "uf_decl_owner", "REQUESTED");
        follow(cancelled, "uf_decl_owner", "REQUESTED");

        mockMvc.perform(as(owner, post("/api/follow-requests/" + declined.id() + "/decline")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(cancelled, delete("/api/users/uf_decl_owner/follow")))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(declined, get("/api/users/uf_decl_owner")))
                .andExpect(jsonPath("$.relationship").value("NONE"));
        mockMvc.perform(as(owner, get("/api/follow-requests")))
                .andExpect(jsonPath("$.items").isEmpty());
        // Only the account that received a request can act on it.
        mockMvc.perform(as(owner, post("/api/follow-requests/" + stranger.id() + "/accept")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, post("/api/follow-requests/" + owner.id() + "/decline")))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptedFollowIsKeptWhenAccountTurnsPrivate() throws Exception {
        TestUser owner = signup("uf_turn_owner");
        TestUser follower = signup("uf_turn_follower");
        follow(follower, "uf_turn_owner", "FOLLOWING");

        makePrivate(owner);

        mockMvc.perform(as(follower, get("/api/users/uf_turn_owner")))
                .andExpect(jsonPath("$.relationship").value("FOLLOWING"));
        mockMvc.perform(as(follower, get("/api/users/uf_turn_owner/followers")))
                .andExpect(status().isOk());
    }

    @Test
    void followRejectsSelfAndUnknownUsers() throws Exception {
        TestUser user = signup("uf_self");

        mockMvc.perform(as(user, post("/api/users/uf_self/follow")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/users/uf_nobody_here/follow")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(as(user, get("/api/users/uf_nobody_here")))
                .andExpect(status().isNotFound());
    }

    @Test
    void followersAreCursorPaginated() throws Exception {
        signup("uf_page_owner");
        for (int i = 0; i < 3; i++) {
            follow(signup("uf_page_f" + i), "uf_page_owner", "FOLLOWING");
        }
        TestUser viewer = signup("uf_page_viewer");

        JsonNode first = json(mockMvc.perform(as(viewer, get("/api/users/uf_page_owner/followers?limit=2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_page_f2", "uf_page_f1")))
                .andReturn().getResponse().getContentAsString());
        String cursor = first.get("nextCursor").asText();

        mockMvc.perform(as(viewer, get("/api/users/uf_page_owner/followers?limit=2&cursor=" + cursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_page_f0")))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void closeFriendsCanBeAddedListedAndRemoved() throws Exception {
        TestUser owner = signup("uf_cf_owner");
        TestUser friend = signup("uf_cf_friend");
        TestUser other = signup("uf_cf_other");

        mockMvc.perform(as(owner, put("/api/close-friends/" + friend.id())))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, put("/api/close-friends/" + friend.id())))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(owner, get("/api/close-friends")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(Matchers.contains("uf_cf_friend")));
        // Each user's list is their own.
        mockMvc.perform(as(other, get("/api/close-friends")))
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(as(owner, put("/api/close-friends/" + owner.id())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(owner, put("/api/close-friends/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(owner, put("/api/close-friends/not-a-uuid")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(owner, delete("/api/close-friends/" + friend.id())))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, get("/api/close-friends")))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private record TestUser(UUID id, String accessToken) {
    }

    private TestUser signup(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2." + NEXT_IP.getAndIncrement());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.test",
                                "password", "Keepsake-password-2026",
                                "displayName", username))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = json(body);
        return new TestUser(UUID.fromString(json.at("/user/id").asText()), json.get("accessToken").asText());
    }

    private void makePrivate(TestUser user) throws Exception {
        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":true}"))
                .andExpect(status().isOk());
    }

    private void follow(TestUser follower, String username, String expectedRelationship) throws Exception {
        mockMvc.perform(as(follower, post("/api/users/" + username + "/follow")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship").value(expectedRelationship));
    }

    private static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + user.accessToken());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
