package com.keepsake.memory;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MemoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemoryRepository memoryRepository;

    @Autowired
    com.keepsake.memory.service.MemoryService memoryService;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void createdMemoryIsProcessingThenReadyWithMediaTagsAndExifDate() throws Exception {
        TestUser author = api.signup("mem_create_author");
        TestUser friend = api.signup("mem_create_friend");
        UUID first = api.upload(author, "image/jpeg",
                TestImages.jpegWithExif(1600, 1200, 1, "2018:02:14 09:00:00", 48.8584, 2.2945));
        UUID second = api.upload(author, "image/png", TestImages.png(300, 300));

        UUID memoryId = api.createMemory(author, List.of(first, second), Map.of(
                "caption", "  Paris trip  ",
                "visibility", "PUBLIC",
                "location", Map.of("name", "Eiffel Tower"),
                "taggedUsernames", List.of("MEM_CREATE_FRIEND")));

        JsonNode ready = api.awaitStatus(author, memoryId, "READY");
        assertThat(ready.get("caption").asText()).isEqualTo("Paris trip");
        assertThat(ready.get("occurredOn").asText()).isEqualTo("2018-02-14");
        assertThat(ready.at("/author/username").asText()).isEqualTo("mem_create_author");
        assertThat(ready.at("/location/name").asText()).isEqualTo("Eiffel Tower");
        // EXIF GPS is only offered to the uploader, never attached silently.
        assertThat(ready.at("/location/latitude").isNull()).isTrue();
        assertThat(ready.at("/taggedPeople/0/username").asText()).isEqualTo("mem_create_friend");
        assertThat(ready.get("media")).hasSize(2);
        assertThat(ready.at("/media/0/position").asInt()).isZero();
        assertThat(ready.at("/media/0/width").asInt()).isEqualTo(1080);
        assertThat(ready.at("/media/0/displayUrl").asText()).contains("display/");
        assertThat(ready.at("/media/0/thumbUrl").asText()).contains("thumb/");
        assertThat(ready.at("/media/1/blurhash").asText()).hasSize(28);
        assertThat(ready.at("/media/0/displayUrl").asText()).doesNotContain("originals/");

        assertThat(api.getMemory(friend, memoryId).get("status").asText()).isEqualTo("READY");
    }

    @Test
    void processingMemoryIsVisibleOnlyToItsAuthor() throws Exception {
        TestUser author = api.signup("mem_proc_author");
        TestUser follower = api.signup("mem_proc_follower");
        api.follow(follower, author);
        UUID uploadId = api.requestUpload(author, "image/jpeg", 1000);

        // Nothing was uploaded yet, so processing fails; until then it is PROCESSING.
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("visibility", "PUBLIC"));

        JsonNode failed = api.awaitStatus(author, memoryId, "FAILED");
        assertThat(failed.at("/media/0/displayUrl").isNull()).isTrue();
        mockMvc.perform(as(follower, get("/api/memories/" + memoryId))).andExpect(status().isNotFound());
    }

    @Test
    void failedMemoryCanBeRetriedOnceTheUploadIsFixed() throws Exception {
        TestUser author = api.signup("mem_retry");
        byte[] image = TestImages.jpeg(64, 64);
        UUID uploadId = api.requestUpload(author, "image/jpeg", image.length);
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("visibility", "PUBLIC"));
        api.awaitStatus(author, memoryId, "FAILED");

        assertThat(api.putUpload(author, uploadId, image)).isEqualTo(200);
        mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/retry")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        api.awaitStatus(author, memoryId, "READY");
        mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/retry")))
                .andExpect(status().isConflict());
    }

    @Test
    void memoryStuckInProcessingIsFailedBySweepAndCanBeRetried() throws Exception {
        TestUser author = api.signup("mem_stuck");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        // Simulate processing lost to a restart: memory and upload left PROCESSING, last touched long ago.
        jdbc.update("UPDATE memories SET status = 'PROCESSING', updated_at = NOW() - INTERVAL '1 hour' WHERE id = ?",
                memoryId);
        jdbc.update("UPDATE media_uploads SET status = 'PROCESSING' WHERE id IN "
                + "(SELECT upload_id FROM memory_media WHERE memory_id = ?)", memoryId);
        mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/retry"))).andExpect(status().isConflict());

        assertThat(memoryService.failStaleProcessing(java.time.Duration.ofMinutes(10))).isGreaterThanOrEqualTo(1);

        assertThat(api.getMemory(author, memoryId).get("status").asText()).isEqualTo("FAILED");
        mockMvc.perform(as(author, post("/api/memories/" + memoryId + "/retry"))).andExpect(status().isAccepted());
        api.awaitStatus(author, memoryId, "READY");
    }

    @Test
    void sweepLeavesRecentlyStartedProcessingAlone() throws Exception {
        TestUser author = api.signup("mem_fresh_processing");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        jdbc.update("UPDATE memories SET status = 'PROCESSING', updated_at = NOW() WHERE id = ?", memoryId);

        memoryService.failStaleProcessing(java.time.Duration.ofMinutes(10));

        assertThat(memoryRepository.findById(memoryId).orElseThrow().getStatus().name()).isEqualTo("PROCESSING");
    }

    @Test
    void implausibleExifDateFallsBackToToday() throws Exception {
        TestUser author = api.signup("mem_bad_exif");
        UUID future = api.upload(author, "image/jpeg",
                TestImages.jpegWithExif(64, 64, 1, "2099:01:01 10:00:00", 1.0, 1.0));
        UUID ancient = api.upload(author, "image/jpeg",
                TestImages.jpegWithExif(64, 64, 1, "1850:06:01 10:00:00", 1.0, 1.0));

        UUID fromFuture = api.createMemory(author, List.of(future), Map.of());
        UUID fromAncient = api.createMemory(author, List.of(ancient), Map.of());

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(api.getMemory(author, fromFuture).get("occurredOn").asText()).isEqualTo(today);
        assertThat(api.getMemory(author, fromAncient).get("occurredOn").asText()).isEqualTo(today);
    }

    @Test
    void occurredOnUsesExplicitValueThenExifThenToday() throws Exception {
        TestUser author = api.signup("mem_dates");
        UUID withExif = api.upload(author, "image/jpeg",
                TestImages.jpegWithExif(64, 64, 1, "2015:05:05 10:00:00", 1.0, 1.0));
        UUID withoutExif = api.upload(author, "image/jpeg", TestImages.jpeg(64, 64));
        UUID explicit = api.upload(author, "image/jpeg",
                TestImages.jpegWithExif(64, 64, 1, "2015:05:05 10:00:00", 1.0, 1.0));

        UUID fromExif = api.createMemory(author, List.of(withExif), Map.of());
        UUID fromToday = api.createMemory(author, List.of(withoutExif), Map.of());
        UUID fromRequest = api.createMemory(author, List.of(explicit), Map.of("occurredOn", "2020-12-25"));

        assertThat(api.getMemory(author, fromExif).get("occurredOn").asText()).isEqualTo("2015-05-05");
        assertThat(api.getMemory(author, fromToday).get("occurredOn").asText())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        assertThat(api.getMemory(author, fromRequest).get("occurredOn").asText()).isEqualTo("2020-12-25");
    }

    @Test
    void detailFollowsVisibilityRulesAndHidesWithNotFound() throws Exception {
        TestUser author = api.signup("mem_vis_author");
        TestUser follower = api.signup("mem_vis_follower");
        TestUser closeFriend = api.signup("mem_vis_close");
        TestUser stranger = api.signup("mem_vis_stranger");
        api.follow(follower, author);
        api.addCloseFriend(author, closeFriend);

        UUID publicMemory = api.createReadyMemory(author, "PUBLIC");
        UUID followersMemory = api.createReadyMemory(author, "FOLLOWERS");
        UUID closeFriendsMemory = api.createReadyMemory(author, "CLOSE_FRIENDS");
        UUID onlyMeMemory = api.createReadyMemory(author, "ONLY_ME");

        expectVisible(stranger, publicMemory, true);
        expectVisible(stranger, followersMemory, false);
        expectVisible(follower, followersMemory, true);
        expectVisible(follower, closeFriendsMemory, false);
        expectVisible(closeFriend, closeFriendsMemory, true);
        expectVisible(follower, onlyMeMemory, false);
        expectVisible(author, onlyMeMemory, true);
    }

    @Test
    void privateAccountHidesPublicMemoriesFromStrangersAndPendingFollowers() throws Exception {
        TestUser author = api.signup("mem_priv_author");
        TestUser pending = api.signup("mem_priv_pending");
        TestUser follower = api.signup("mem_priv_follower");
        api.makePrivate(author);
        api.requestFollow(pending, author);
        api.follow(follower, author);

        UUID memoryId = api.createReadyMemory(author, "PUBLIC");

        expectVisible(pending, memoryId, false);
        expectVisible(follower, memoryId, true);
        mockMvc.perform(as(pending, get("/api/memories/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void createValidatesInput() throws Exception {
        TestUser author = api.signup("mem_invalid");
        TestUser other = api.signup("mem_invalid_other");
        UUID upload = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID othersUpload = api.upload(other, "image/jpeg", TestImages.jpeg(16, 16));

        expectCreateRejected(author, Map.of("uploadIds", List.of()), "between 1 and 10");
        expectCreateRejected(author, Map.of("uploadIds", java.util.Collections.nCopies(11, upload)),
                "between 1 and 10");
        expectCreateRejected(author, Map.of("uploadIds", List.of(othersUpload)), "not available");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload), "caption", "x".repeat(2201)), "caption");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload),
                "location", Map.of("latitude", 10.0)), "together");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload),
                "location", Map.of("latitude", 91.0, "longitude", 0.0)), "range");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload),
                "occurredOn", LocalDate.now(ZoneOffset.UTC).plusDays(5).toString()), "occurredOn");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload),
                "taggedUsernames", List.of("mem_nobody")), "Unknown user");
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload),
                "taggedUsernames", List.of("mem_invalid")), "tag yourself");

        Map<String, Object> noVisibility = new HashMap<>(Map.of("uploadIds", List.of(upload)));
        noVisibility.put("visibility", null);
        mockMvc.perform(as(author, post("/api/memories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(noVisibility)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(author, post("/api/memories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadIds\":[\"" + upload + "\"],\"visibility\":\"EVERYONE\"}"))
                .andExpect(status().isBadRequest());

        // A rejected request must not consume the upload.
        api.createMemory(author, List.of(upload), Map.of());
        expectCreateRejected(author, Map.of("uploadIds", List.of(upload)), "not available");
    }

    @Test
    void authorCanEditCaptionDateLocationVisibilityAndTags() throws Exception {
        TestUser author = api.signup("mem_edit_author");
        TestUser first = api.signup("mem_edit_tag_a");
        api.signup("mem_edit_tag_b");
        TestUser follower = api.signup("mem_edit_follower");
        api.follow(follower, author);
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(32, 32));
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of(
                "caption", "Before",
                "location", Map.of("name", "Somewhere", "latitude", 10.0, "longitude", 20.0),
                "taggedUsernames", List.of(first.username())));
        api.awaitStatus(author, memoryId, "READY");

        mockMvc.perform(as(author, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caption":"After","occurredOn":"2021-03-04","location":null,
                                 "visibility":"ONLY_ME","taggedUsernames":["mem_edit_tag_b"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("After"))
                .andExpect(jsonPath("$.occurredOn").value("2021-03-04"))
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.visibility").value("ONLY_ME"))
                .andExpect(jsonPath("$.taggedPeople[*].username").value(Matchers.contains("mem_edit_tag_b")));
        expectVisible(follower, memoryId, false);

        // Absent fields are unchanged; explicit null clears the caption.
        mockMvc.perform(as(author, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").doesNotExist())
                .andExpect(jsonPath("$.occurredOn").value("2021-03-04"))
                .andExpect(jsonPath("$.visibility").value("ONLY_ME"));
        mockMvc.perform(as(author, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorCanEditOrDeleteAndOthersSeeNotFound() throws Exception {
        TestUser author = api.signup("mem_owner");
        TestUser follower = api.signup("mem_owner_follower");
        api.follow(follower, author);
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");

        mockMvc.perform(as(follower, patch("/api/memories/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"hijack\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(follower, delete("/api/memories/" + memoryId))).andExpect(status().isNotFound());
        mockMvc.perform(as(follower, post("/api/memories/" + memoryId + "/retry"))).andExpect(status().isNotFound());

        mockMvc.perform(as(author, delete("/api/memories/" + memoryId))).andExpect(status().isNoContent());

        mockMvc.perform(as(author, get("/api/memories/" + memoryId))).andExpect(status().isNotFound());
        mockMvc.perform(as(follower, get("/api/memories/" + memoryId))).andExpect(status().isNotFound());
        mockMvc.perform(as(author, delete("/api/memories/" + memoryId))).andExpect(status().isNotFound());
        Memory stored = memoryRepository.findById(memoryId).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
    }

    @Test
    void createIsRateLimitedPerUser() throws Exception {
        TestUser author = api.signup("mem_rate");
        for (int attempt = 0; attempt < 30; attempt++) {
            mockMvc.perform(as(author, post("/api/memories"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"visibility\":\"PUBLIC\",\"uploadIds\":[]}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(as(author, post("/api/memories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"uploadIds\":[]}"))
                .andExpect(status().isTooManyRequests());
    }

    private void expectVisible(TestUser viewer, UUID memoryId, boolean visible) throws Exception {
        mockMvc.perform(as(viewer, get("/api/memories/" + memoryId)))
                .andExpect(visible ? status().isOk() : status().isNotFound());
    }

    private void expectCreateRejected(TestUser author, Map<String, Object> body, String detail) throws Exception {
        Map<String, Object> request = new HashMap<>(body);
        request.putIfAbsent("visibility", "PUBLIC");
        mockMvc.perform(as(author, post("/api/memories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString(detail)));
    }
}
