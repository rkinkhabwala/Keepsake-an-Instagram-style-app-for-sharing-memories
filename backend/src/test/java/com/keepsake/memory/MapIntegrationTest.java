package com.keepsake.memory;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 2 acceptance: the map only ever returns memories the viewer may see (mixed visibility). */
class MapIntegrationTest extends IntegrationTestBase {

    /** A box no other test class places memories in, so results here are only this class's. */
    private static final String ATLANTIC = "-40,10,-20,30";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void returnsOnlyOwnFollowedAndTaggedMemoriesTheViewerMaySee() throws Exception {
        TestUser viewer = api.signup("map_viewer");
        TestUser followed = api.signup("map_followed");
        TestUser closeFriendOf = api.signup("map_cf_author");
        TestUser privateFollowed = api.signup("map_private");
        TestUser stranger = api.signup("map_stranger");
        TestUser blocker = api.signup("map_blocker");
        api.follow(viewer, followed);
        api.follow(viewer, closeFriendOf);
        api.addCloseFriend(closeFriendOf, viewer);
        api.makePrivate(privateFollowed);
        api.follow(viewer, privateFollowed);
        api.follow(viewer, blocker);

        List<String> expected = new ArrayList<>();
        for (String visibility : List.of("PUBLIC", "FOLLOWERS", "CLOSE_FRIENDS", "ONLY_ME")) {
            expected.add(memory(viewer, visibility, 20, -30, List.of()));
        }
        expected.add(memory(followed, "PUBLIC", 21, -31, List.of()));
        expected.add(memory(followed, "FOLLOWERS", 21, -31, List.of()));
        String followedCloseFriends = memory(followed, "CLOSE_FRIENDS", 21, -31, List.of());
        String followedOnlyMe = memory(followed, "ONLY_ME", 21, -31, List.of());
        expected.add(memory(closeFriendOf, "CLOSE_FRIENDS", 22, -32, List.of()));
        expected.add(memory(privateFollowed, "FOLLOWERS", 23, -33, List.of()));
        String strangerPublic = memory(stranger, "PUBLIC", 24, -34, List.of());
        expected.add(memory(stranger, "PUBLIC", 24, -34, List.of(viewer.username())));
        String strangerHiddenTag = memory(stranger, "ONLY_ME", 24, -34, List.of(viewer.username()));
        String blockedAuthor = memory(blocker, "PUBLIC", 25, -35, List.of());
        api.block(blocker, viewer);
        String noCoordinates = memoryWithoutLocation(viewer);
        String outsideBox = memory(viewer, "PUBLIC", 45, 5, List.of());

        List<String> onMap = mapIds(viewer, ATLANTIC);

        assertThat(onMap).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(onMap).doesNotContain(followedCloseFriends, followedOnlyMe, strangerPublic, strangerHiddenTag,
                blockedAuthor, noCoordinates, outsideBox);
    }

    @Test
    void boxesFilterByCoordinatesIncludingAcrossTheAntimeridian() throws Exception {
        TestUser viewer = api.signup("map_box_viewer");
        String fiji = memory(viewer, "PUBLIC", -17.7, 178.1, List.of());
        String samoa = memory(viewer, "PUBLIC", -13.8, -171.8, List.of());
        String edge = memory(viewer, "PUBLIC", -10.0, 170.0, List.of());

        assertThat(mapIds(viewer, "170,-20,-170,-10")).containsExactlyInAnyOrder(fiji, samoa, edge);
        assertThat(mapIds(viewer, "175,-20,180,-15")).containsExactly(fiji);
        assertThat(mapIds(viewer, "-180,-20,-170,-10")).containsExactly(samoa);
        assertThat(mapIds(viewer, "0,0,10,10")).isEmpty();
    }

    @Test
    void rejectsMissingOrInvalidBoxes() throws Exception {
        TestUser viewer = api.signup("map_invalid");

        mockMvc.perform(as(viewer, get("/api/memories/map"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(viewer, get("/api/memories/map").param("bbox", "0,10,10,5")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/memories/map").param("bbox", ATLANTIC)).andExpect(status().isUnauthorized());
    }

    private String memory(TestUser author, String visibility, double lat, double lng, List<String> tagged)
            throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of(
                "visibility", visibility,
                "taggedUsernames", tagged,
                "location", Map.of("name", "Somewhere", "latitude", lat, "longitude", lng)));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId.toString();
    }

    private String memoryWithoutLocation(TestUser author) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID memoryId = api.createMemory(author, List.of(uploadId),
                Map.of("visibility", "PUBLIC", "location", Map.of("name", "Atlantic, roughly")));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId.toString();
    }

    private List<String> mapIds(TestUser viewer, String bbox) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/memories/map").param("bbox", bbox)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(body.get("truncated").asBoolean()).isFalse();
        List<String> ids = new ArrayList<>();
        body.get("items").forEach(item -> {
            assertThat(item.get("thumbUrl").asText()).contains("thumb/");
            ids.add(item.get("id").asText());
        });
        return ids;
    }
}
