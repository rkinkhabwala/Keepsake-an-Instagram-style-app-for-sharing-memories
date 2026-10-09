package com.keepsake.engagement;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.engagement.entity.Comment;
import com.keepsake.engagement.repository.CommentRepository;
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

class EngagementIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommentRepository commentRepository;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void likesAreIdempotentAndCountedAndShownToTheViewer() throws Exception {
        TestUser author = api.signup("eng_like_author");
        TestUser fan = api.signup("eng_like_fan");
        TestUser other = api.signup("eng_like_other");
        api.follow(fan, author);
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");

        mockMvc.perform(as(fan, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(fan, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(other, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());

        JsonNode seenByFan = api.getMemory(fan, memoryId);
        assertThat(seenByFan.get("likeCount").asInt()).isEqualTo(2);
        assertThat(seenByFan.get("likedByViewer").asBoolean()).isTrue();
        assertThat(api.getMemory(author, memoryId).get("likedByViewer").asBoolean()).isFalse();
        mockMvc.perform(as(fan, get("/api/feed")))
                .andExpect(jsonPath("$.items[0].likedByViewer").value(true))
                .andExpect(jsonPath("$.items[0].likeCount").value(2));

        mockMvc.perform(as(fan, delete("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        mockMvc.perform(as(fan, delete("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        assertThat(api.getMemory(fan, memoryId).get("likeCount").asInt()).isEqualTo(1);
    }

    @Test
    void engagementOnHiddenMemoriesIsNotFound() throws Exception {
        TestUser author = api.signup("eng_hidden_author");
        TestUser stranger = api.signup("eng_hidden_stranger");
        UUID followersOnly = api.createReadyMemory(author, "FOLLOWERS");

        mockMvc.perform(as(stranger, put("/api/memories/" + followersOnly + "/like"))).andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, put("/api/memories/" + followersOnly + "/save"))).andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, get("/api/memories/" + followersOnly + "/comments")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, post("/api/memories/" + followersOnly + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, put("/api/memories/" + UUID.randomUUID() + "/like")))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentsNestOneLevelAndListOldestFirst() throws Exception {
        TestUser author = api.signup("eng_thread_author");
        TestUser alice = api.signup("eng_thread_alice");
        TestUser bob = api.signup("eng_thread_bob");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        UUID otherMemory = api.createReadyMemory(author, "PUBLIC");

        UUID first = comment(alice, memoryId, "  First!  ", null);
        UUID reply = comment(bob, memoryId, "Reply to first", first);
        UUID second = comment(bob, memoryId, "Second", null);

        expectCommentRejected(alice, memoryId, Map.of("body", "nested", "parentId", reply.toString()), "top-level");
        comment(alice, memoryId, "Reply to second", second);
        expectCommentRejected(alice, otherMemory, Map.of("body", "wrong memory", "parentId", first.toString()),
                "not a comment on this memory");
        expectCommentRejected(alice, memoryId, Map.of("body", "   "), "between 1 and 500");
        expectCommentRejected(alice, memoryId, Map.of("body", "x".repeat(501)), "between 1 and 500");

        mockMvc.perform(as(author, get("/api/memories/" + memoryId + "/comments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].body").value(Matchers.contains("First!", "Second")))
                .andExpect(jsonPath("$.items[0].author.username").value("eng_thread_alice"))
                .andExpect(jsonPath("$.items[0].replies[*].body").value(Matchers.contains("Reply to first")))
                .andExpect(jsonPath("$.items[0].replies[0].parentId").value(first.toString()))
                .andExpect(jsonPath("$.items[1].replies[*].body").value(Matchers.contains("Reply to second")))
                .andExpect(jsonPath("$.items[0].canDelete").value(true));
        // Only the comment's author and the memory's author may delete it.
        mockMvc.perform(as(bob, get("/api/memories/" + memoryId + "/comments")))
                .andExpect(jsonPath("$.items[0].canDelete").value(false))
                .andExpect(jsonPath("$.items[1].canDelete").value(true));

        JsonNode memory = api.getMemory(alice, memoryId);
        assertThat(memory.get("commentCount").asInt()).isEqualTo(4);
        List<String> latest = new ArrayList<>();
        memory.get("latestComments").forEach(comment -> latest.add(comment.get("body").asText()));
        // Feed cards preview the latest two top-level comments, oldest first.
        assertThat(latest).containsExactly("First!", "Second");
    }

    @Test
    void databaseRejectsRepliesToRepliesEvenIfTheServiceIsBypassed() throws Exception {
        TestUser author = api.signup("eng_trigger");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        UUID top = comment(author, memoryId, "top", null);
        UUID reply = comment(author, memoryId, "reply", top);

        assertThatThrownBy(() -> commentRepository.saveAndFlush(new Comment(memoryId, author.id(), reply, "deep")))
                .hasMessageContaining("top-level comment");
    }

    @Test
    void commentDeletionRulesPlaceholdersAndCounts() throws Exception {
        TestUser author = api.signup("eng_del_author");
        TestUser commenter = api.signup("eng_del_commenter");
        TestUser bystander = api.signup("eng_del_bystander");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        UUID withReply = comment(commenter, memoryId, "has a reply", null);
        comment(bystander, memoryId, "the reply", withReply);
        UUID lonely = comment(commenter, memoryId, "no replies", null);
        UUID byBystander = comment(bystander, memoryId, "remove me", null);

        mockMvc.perform(as(bystander, delete("/api/comments/" + lonely))).andExpect(status().isNotFound());
        mockMvc.perform(as(commenter, delete("/api/comments/" + withReply))).andExpect(status().isNoContent());
        mockMvc.perform(as(commenter, delete("/api/comments/" + lonely))).andExpect(status().isNoContent());
        // The memory's author can remove anyone's comment on it.
        mockMvc.perform(as(author, delete("/api/comments/" + byBystander))).andExpect(status().isNoContent());
        mockMvc.perform(as(author, delete("/api/comments/" + byBystander))).andExpect(status().isNotFound());

        mockMvc.perform(as(bystander, get("/api/memories/" + memoryId + "/comments")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.items[0].replies[0].body").value("the reply"));
        assertThat(api.getMemory(author, memoryId).get("commentCount").asInt()).isEqualTo(1);

        expectCommentRejected(bystander, memoryId, Map.of("body", "late", "parentId", withReply.toString()),
                "deleted comment");
    }

    @Test
    void commentsArePaginatedByTopLevelComment() throws Exception {
        TestUser author = api.signup("eng_page");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        for (int i = 0; i < 3; i++) {
            comment(author, memoryId, "c" + i, null);
        }

        JsonNode first = objectMapper.readTree(mockMvc.perform(
                        as(author, get("/api/memories/" + memoryId + "/comments?limit=2")))
                .andReturn().getResponse().getContentAsString());
        assertThat(first.get("items")).hasSize(2);
        mockMvc.perform(as(author, get("/api/memories/" + memoryId + "/comments?limit=2&cursor="
                        + first.get("nextCursor").asText())))
                .andExpect(jsonPath("$.items[*].body").value(Matchers.contains("c2")))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void savedListIsPrivateOrderedBySaveTimeAndRespectsLaterVisibilityChanges() throws Exception {
        TestUser author = api.signup("eng_save_author");
        TestUser saver = api.signup("eng_save_saver");
        UUID older = api.createReadyMemory(author, "PUBLIC");
        UUID newer = api.createReadyMemory(author, "PUBLIC");

        mockMvc.perform(as(saver, put("/api/memories/" + newer + "/save"))).andExpect(status().isNoContent());
        mockMvc.perform(as(saver, put("/api/memories/" + older + "/save"))).andExpect(status().isNoContent());
        mockMvc.perform(as(saver, put("/api/memories/" + older + "/save"))).andExpect(status().isNoContent());

        assertThat(savedIds(saver, 50)).containsExactly(older.toString(), newer.toString());
        assertThat(api.getMemory(saver, older).get("savedByViewer").asBoolean()).isTrue();
        assertThat(savedIds(author, 50)).isEmpty();

        JsonNode firstPage = objectMapper.readTree(mockMvc.perform(as(saver, get("/api/saved?limit=1")))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(as(saver, get("/api/saved?limit=1&cursor=" + firstPage.get("nextCursor").asText())))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(newer.toString())));

        // The cursor keeps working after the memory it points at is unsaved.
        mockMvc.perform(as(saver, delete("/api/memories/" + older + "/save"))).andExpect(status().isNoContent());
        mockMvc.perform(as(saver, get("/api/saved?limit=1&cursor=" + firstPage.get("nextCursor").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(newer.toString())));
        mockMvc.perform(as(saver, get("/api/saved?cursor=garbage"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(saver, put("/api/memories/" + older + "/save"))).andExpect(status().isNoContent());
        assertThat(savedIds(saver, 50)).containsExactly(older.toString(), newer.toString());

        // The author hides a saved memory: it drops out of the saved list, and can still be unsaved.
        mockMvc.perform(as(author, patch("/api/memories/" + older))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"ONLY_ME\"}"))
                .andExpect(status().isOk());
        assertThat(savedIds(saver, 50)).containsExactly(newer.toString());
        mockMvc.perform(as(saver, delete("/api/memories/" + older + "/save"))).andExpect(status().isNoContent());
        mockMvc.perform(as(saver, delete("/api/memories/" + newer + "/save"))).andExpect(status().isNoContent());
        assertThat(savedIds(saver, 50)).isEmpty();
    }

    @Test
    void taggedTabShowsOnlyMemoriesTheViewerMaySee() throws Exception {
        TestUser author = api.signup("eng_tag_author");
        TestUser tagged = api.signup("eng_tag_person");
        TestUser follower = api.signup("eng_tag_follower");
        TestUser stranger = api.signup("eng_tag_stranger");
        api.follow(follower, author);
        UUID publicMemory = taggedMemory(author, "PUBLIC", tagged);
        UUID followersMemory = taggedMemory(author, "FOLLOWERS", tagged);
        api.createReadyMemory(author, "PUBLIC");

        assertThat(gridIds(stranger, "/api/users/eng_tag_person/tagged")).containsExactly(publicMemory.toString());
        assertThat(gridIds(follower, "/api/users/eng_tag_person/tagged"))
                .containsExactly(followersMemory.toString(), publicMemory.toString());
        // Being tagged does not grant access to a memory the tagged person otherwise could not see.
        assertThat(gridIds(tagged, "/api/users/eng_tag_person/tagged")).containsExactly(publicMemory.toString());
    }

    @Test
    void commentsAreRateLimitedPerUser() throws Exception {
        TestUser author = api.signup("eng_rate");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/comments"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"one too many\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private UUID taggedMemory(TestUser author, String visibility, TestUser tagged) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(32, 32));
        UUID memoryId = api.createMemory(author, List.of(uploadId),
                Map.of("visibility", visibility, "taggedUsernames", List.of(tagged.username())));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId;
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

    private void expectCommentRejected(TestUser user, UUID memoryId, Map<String, Object> body, String detail)
            throws Exception {
        mockMvc.perform(as(user, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString(detail)));
    }

    private List<String> savedIds(TestUser user, int limit) throws Exception {
        return gridIds(user, "/api/saved?limit=" + limit);
    }

    private List<String> gridIds(TestUser viewer, String path) throws Exception {
        JsonNode page = objectMapper.readTree(mockMvc.perform(as(viewer, get(path)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
