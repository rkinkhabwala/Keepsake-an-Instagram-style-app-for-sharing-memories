package com.keepsake.user;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AvatarIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    com.keepsake.user.service.UserService userService;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void avatarUploadBecomesSquareThumbShownOnProfileAndInLists() throws Exception {
        TestUser user = api.signup("av_user");
        TestUser follower = api.signup("av_follower");
        api.follow(follower, user);
        UUID uploadId = api.upload(user, "image/png", TestImages.png(400, 300));

        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk());

        TestApi.await(Duration.ofSeconds(30), () -> !me(user).get("avatarUrl").isNull());
        assertThat(me(user).get("avatarUrl").asText()).contains("thumb/" + uploadId);
        mockMvc.perform(as(follower, get("/api/users/av_user")))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.containsString("thumb/")));
        mockMvc.perform(as(user, get("/api/users/av_user/followers")))
                .andExpect(jsonPath("$.items[0].avatarUrl").doesNotExist());
        mockMvc.perform(as(follower, get("/api/users/av_follower/following")))
                .andExpect(jsonPath("$.items[0].avatarUrl").value(org.hamcrest.Matchers.containsString("thumb/")));
    }

    @Test
    void onlyTheMostRecentlyRequestedAvatarIsApplied() throws Exception {
        TestUser user = api.signup("av_order");
        UUID first = api.upload(user, "image/png", TestImages.png(50, 50));
        UUID second = api.upload(user, "image/png", TestImages.png(60, 60));
        for (UUID uploadId : List.of(first, second)) {
            mockMvc.perform(as(user, patch("/api/users/me"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"avatarUploadId\":\"" + uploadId + "\"}"))
                    .andExpect(status().isOk());
        }
        TestApi.await(Duration.ofSeconds(30), () -> me(user).get("avatarUrl").asText("").contains(second.toString()));

        // The older request finishing late must not replace the newer avatar.
        userService.applyAvatar(user.id(), first);

        assertThat(me(user).get("avatarUrl").asText()).contains("thumb/" + second);
    }

    @Test
    void avatarMustBeAnUnusedUploadOfYourOwn() throws Exception {
        TestUser user = api.signup("av_owner");
        TestUser other = api.signup("av_other");
        UUID othersUpload = api.upload(other, "image/png", TestImages.png(10, 10));

        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUploadId\":\"" + othersUpload + "\",\"bio\":\"unchanged?\"}"))
                .andExpect(status().isBadRequest());
        // The whole update is rejected, not just the avatar.
        assertThat(me(user).get("bio").isNull()).isTrue();
    }

    private JsonNode me(TestUser user) {
        try {
            return objectMapper.readTree(mockMvc.perform(as(user, get("/api/users/me")))
                    .andReturn().getResponse().getContentAsString());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
