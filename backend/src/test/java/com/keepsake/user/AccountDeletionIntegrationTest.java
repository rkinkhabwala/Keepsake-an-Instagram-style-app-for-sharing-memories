package com.keepsake.user;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaStorage;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AccountDeletionIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Keepsake-password-2026";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaUploadRepository uploadRepository;
    @Autowired MediaStorage storage;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void deletingAnAccountLeavesNothingOfTheUserInPostgresOrStorage() throws Exception {
        TestUser leaving = api.signup("del_leaving");
        TestUser friend = api.signup("del_friend");
        TestUser other = api.signup("del_other");
        api.makePrivate(leaving);
        api.follow(friend, leaving);
        api.follow(leaving, friend);
        api.requestFollow(other, leaving);
        api.addCloseFriend(leaving, friend);
        api.addCloseFriend(friend, leaving);

        // The leaving user's own content: a two-photo memory tagging a friend, an avatar, and an unused upload.
        UUID ownMemory = api.createMemory(leaving, List.of(
                        api.upload(leaving, "image/jpeg", TestImages.jpeg(80, 60)),
                        api.upload(leaving, "image/png", TestImages.png(40, 40))),
                Map.of("visibility", "PUBLIC", "taggedUsernames", List.of(friend.username())));
        api.awaitStatus(leaving, ownMemory, "READY");
        UUID avatarUpload = api.upload(leaving, "image/png", TestImages.png(30, 30));
        mockMvc.perform(as(leaving, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUploadId\":\"" + avatarUpload + "\"}"))
                .andExpect(status().isOk());
        TestApi.await(Duration.ofSeconds(30), () -> uploadStatus(avatarUpload).equals("READY"));
        api.upload(leaving, "image/jpeg", TestImages.jpeg(20, 20));

        // Their activity on a friend's memory, which tags them: a like, a save, a comment with a reply.
        UUID friendsMemory = api.createMemory(friend,
                List.of(api.upload(friend, "image/jpeg", TestImages.jpeg(50, 50))),
                Map.of("visibility", "PUBLIC", "taggedUsernames", List.of(leaving.username())));
        api.awaitStatus(friend, friendsMemory, "READY");
        mockMvc.perform(as(leaving, put("/api/memories/" + friendsMemory + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(other, put("/api/memories/" + friendsMemory + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(leaving, put("/api/memories/" + friendsMemory + "/save"))).andExpect(status().isNoContent());
        UUID comment = comment(leaving, friendsMemory, null);
        comment(other, friendsMemory, comment);
        comment(other, friendsMemory, null);
        // Others' activity on the leaving user's memory.
        mockMvc.perform(as(friend, put("/api/memories/" + ownMemory + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(friend, put("/api/memories/" + ownMemory + "/save"))).andExpect(status().isNoContent());
        comment(friend, ownMemory, null);

        List<String> keys = new ArrayList<>();
        for (MediaUpload upload : uploadRepository.findAllByOwnerId(leaving.id())) {
            keys.add(upload.getOriginalKey());
            if (upload.getDisplayKey() != null) {
                keys.add(upload.getDisplayKey());
                keys.add(upload.getThumbKey());
            }
        }
        // Four originals (two memory photos, the avatar, the unused upload) and display + thumb for the three processed.
        assertThat(keys).hasSize(4 + 3 * 2);
        assertThat(keys).allSatisfy(key -> assertThat(storage.size(key)).isPresent());

        mockMvc.perform(as(leaving, delete("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"not-my-password\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", leaving.id())).isOne();

        mockMvc.perform(as(leaving, delete("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));

        UUID id = leaving.id();
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followee_id = ?", id, id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM close_friends WHERE owner_id = ? OR friend_id = ?", id, id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM media_uploads WHERE owner_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM memories WHERE author_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_media WHERE memory_id = ?", ownMemory)).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_people WHERE user_id = ? OR memory_id = ?", id, ownMemory)).isZero();
        assertThat(count("SELECT COUNT(*) FROM likes WHERE user_id = ? OR memory_id = ?", id, ownMemory)).isZero();
        assertThat(count("SELECT COUNT(*) FROM saves WHERE user_id = ? OR memory_id = ?", id, ownMemory)).isZero();
        assertThat(count("SELECT COUNT(*) FROM comments WHERE author_id = ? OR memory_id = ? OR parent_id = ?",
                id, ownMemory, comment)).isZero();
        assertThat(keys).allSatisfy(key -> assertThat(storage.size(key)).isEmpty());

        // Other people's memories keep correct counts and lose nothing else.
        assertThat(api.getMemory(friend, friendsMemory).get("likeCount").asInt()).isEqualTo(1);
        assertThat(api.getMemory(friend, friendsMemory).get("commentCount").asInt()).isEqualTo(1);
        assertThat(api.getMemory(friend, friendsMemory).get("taggedPeople")).isEmpty();
        // The old access token no longer works.
        mockMvc.perform(as(leaving, get("/api/users/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void deletionRequiresThePassword() throws Exception {
        TestUser user = api.signup("del_nopass");

        mockMvc.perform(as(user, delete("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", user.id())).isOne();
    }

    private UUID comment(TestUser user, UUID memoryId, UUID parentId) throws Exception {
        String body = parentId == null ? "{\"body\":\"hello\"}" : "{\"body\":\"reply\",\"parentId\":\"" + parentId + "\"}";
        String response = mockMvc.perform(as(user, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String uploadStatus(UUID uploadId) {
        return jdbc.queryForObject("SELECT status FROM media_uploads WHERE id = ?", String.class, uploadId);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }
}
