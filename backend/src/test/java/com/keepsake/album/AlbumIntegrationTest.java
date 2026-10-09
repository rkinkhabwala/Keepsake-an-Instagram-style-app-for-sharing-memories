package com.keepsake.album;

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
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
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
import org.springframework.test.web.servlet.ResultActions;

class AlbumIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    /** Phase 2 acceptance: an album collaborator can add their own memories. */
    @Test
    void editorCollaboratorCanAddTheirOwnMemoriesButNotOthers() throws Exception {
        TestUser owner = api.signup("alb_add_owner");
        TestUser editor = api.signup("alb_add_editor");
        TestUser viewerCollab = api.signup("alb_add_viewer");
        api.follow(editor, owner);
        api.follow(viewerCollab, owner);
        UUID album = album(owner, "Trip", "PUBLIC");
        collaborator(owner, album, editor, "EDITOR").andExpect(status().isNoContent());
        collaborator(owner, album, viewerCollab, "VIEWER").andExpect(status().isNoContent());
        UUID editorsMemory = api.createReadyMemory(editor, "PUBLIC");
        UUID ownersMemory = api.createReadyMemory(owner, "PUBLIC");
        UUID viewersMemory = api.createReadyMemory(viewerCollab, "PUBLIC");

        add(editor, album, editorsMemory).andExpect(status().isNoContent());
        add(editor, album, editorsMemory).andExpect(status().isNoContent());
        add(owner, album, ownersMemory).andExpect(status().isNoContent());
        add(editor, album, ownersMemory).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can only add your own memories"));
        add(viewerCollab, album, viewersMemory).andExpect(status().isForbidden());

        assertThat(memoryIds(owner, album)).containsExactly(editorsMemory.toString(), ownersMemory.toString());
        mockMvc.perform(as(editor, get("/api/albums/" + album)))
                .andExpect(jsonPath("$.viewerRole").value("EDITOR"))
                .andExpect(jsonPath("$.collaborators[*].user.username")
                        .value(Matchers.contains("alb_add_editor", "alb_add_viewer")));
    }

    /** Phase 2 acceptance: an album viewer only sees memories they'd be allowed to see individually. */
    @Test
    void albumViewersOnlySeeMemoriesTheyCouldSeeIndividually() throws Exception {
        TestUser owner = api.signup("alb_vis_owner");
        TestUser editor = api.signup("alb_vis_editor");
        TestUser ownersFollower = api.signup("alb_vis_follower");
        TestUser editorsCloseFriend = api.signup("alb_vis_cf");
        TestUser stranger = api.signup("alb_vis_stranger");
        api.follow(editor, owner);
        api.follow(ownersFollower, owner);
        api.follow(editorsCloseFriend, owner);
        api.addCloseFriend(editor, editorsCloseFriend);
        UUID album = album(owner, "Shared", "PUBLIC");
        collaborator(owner, album, editor, "EDITOR").andExpect(status().isNoContent());

        UUID ownerPublic = api.createReadyMemory(owner, "PUBLIC");
        UUID ownerFollowers = api.createReadyMemory(owner, "FOLLOWERS");
        UUID ownerOnlyMe = api.createReadyMemory(owner, "ONLY_ME");
        UUID editorPublic = api.createReadyMemory(editor, "PUBLIC");
        UUID editorCloseFriends = api.createReadyMemory(editor, "CLOSE_FRIENDS");
        add(owner, album, ownerPublic, ownerFollowers, ownerOnlyMe).andExpect(status().isNoContent());
        add(editor, album, editorPublic, editorCloseFriends).andExpect(status().isNoContent());

        assertThat(memoryIds(stranger, album)).containsExactly(ownerPublic.toString(), editorPublic.toString());
        assertThat(memoryIds(ownersFollower, album))
                .containsExactly(ownerPublic.toString(), ownerFollowers.toString(), editorPublic.toString());
        assertThat(memoryIds(editorsCloseFriend, album)).containsExactly(ownerPublic.toString(),
                ownerFollowers.toString(), editorPublic.toString(), editorCloseFriends.toString());
        // The owner sees their own ONLY_ME memory, but not the editor's close-friends one.
        assertThat(memoryIds(owner, album)).containsExactly(ownerPublic.toString(), ownerFollowers.toString(),
                ownerOnlyMe.toString(), editorPublic.toString());
        mockMvc.perform(as(stranger, get("/api/albums/" + album))).andExpect(jsonPath("$.memoryCount").value(2))
                .andExpect(jsonPath("$.viewerRole").value("NONE"));
    }

    @Test
    void albumVisibilityDecidesWhoCanOpenItAndCollaboratorsAlwaysCan() throws Exception {
        TestUser owner = api.signup("alb_open_owner");
        TestUser follower = api.signup("alb_open_follower");
        TestUser collaborator = api.signup("alb_open_collab");
        TestUser stranger = api.signup("alb_open_stranger");
        api.follow(follower, owner);
        api.follow(collaborator, owner);
        UUID followersAlbum = album(owner, "Friends", "FOLLOWERS");
        UUID privateAlbum = album(owner, "Just us", "ONLY_ME");
        collaborator(owner, privateAlbum, collaborator, "VIEWER").andExpect(status().isNoContent());

        mockMvc.perform(as(stranger, get("/api/albums/" + followersAlbum))).andExpect(status().isNotFound());
        mockMvc.perform(as(follower, get("/api/albums/" + followersAlbum))).andExpect(status().isOk());
        mockMvc.perform(as(follower, get("/api/albums/" + privateAlbum))).andExpect(status().isNotFound());
        mockMvc.perform(as(collaborator, get("/api/albums/" + privateAlbum)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("VIEWER"));
        mockMvc.perform(as(stranger, get("/api/albums/" + privateAlbum + "/memories"))).andExpect(status().isNotFound());

        List<String> titles = new ArrayList<>();
        page(follower, "/api/users/alb_open_owner/albums").get("items")
                .forEach(album -> titles.add(album.get("title").asText()));
        assertThat(titles).containsExactly("Friends");
        page(collaborator, "/api/users/alb_open_collab/albums").get("items")
                .forEach(album -> assertThat(album.get("title").asText()).isEqualTo("Just us"));
    }

    @Test
    void onlyTheOwnerManagesTheAlbumAndCollaboratorsMustBeFollowers() throws Exception {
        TestUser owner = api.signup("alb_mgmt_owner");
        TestUser editor = api.signup("alb_mgmt_editor");
        TestUser notFollower = api.signup("alb_mgmt_nf");
        TestUser blocked = api.signup("alb_mgmt_blocked");
        api.follow(editor, owner);
        api.follow(blocked, owner);
        UUID album = album(owner, "Managed", "PUBLIC");
        collaborator(owner, album, editor, "EDITOR").andExpect(status().isNoContent());

        collaborator(owner, album, notFollower, "VIEWER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only people who follow you can be added to an album"));
        api.block(owner, blocked);
        collaborator(owner, album, blocked, "VIEWER").andExpect(status().isNotFound());
        collaborator(owner, album, owner, "EDITOR").andExpect(status().isBadRequest());
        mockMvc.perform(as(owner, post("/api/albums/" + album + "/collaborators"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + editor.id() + "\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
        collaborator(editor, album, notFollower, "VIEWER").andExpect(status().isForbidden());
        mockMvc.perform(as(editor, patch("/api/albums/" + album))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, delete("/api/albums/" + album))).andExpect(status().isForbidden());

        mockMvc.perform(as(owner, patch("/api/albums/" + album))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Renamed  \",\"description\":\"\",\"visibility\":\"FOLLOWERS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.visibility").value("FOLLOWERS"));
        mockMvc.perform(as(owner, delete("/api/albums/" + album))).andExpect(status().isNoContent());
        mockMvc.perform(as(owner, get("/api/albums/" + album))).andExpect(status().isNotFound());
    }

    @Test
    void leavingOrBeingRemovedTakesTheCollaboratorsMemoriesOut() throws Exception {
        TestUser owner = api.signup("alb_leave_owner");
        TestUser leaver = api.signup("alb_leave_leaver");
        TestUser removed = api.signup("alb_leave_removed");
        api.follow(leaver, owner);
        api.follow(removed, owner);
        UUID album = album(owner, "Changing", "PUBLIC");
        collaborator(owner, album, leaver, "EDITOR").andExpect(status().isNoContent());
        collaborator(owner, album, removed, "EDITOR").andExpect(status().isNoContent());
        UUID ownersMemory = api.createReadyMemory(owner, "PUBLIC");
        add(owner, album, ownersMemory).andExpect(status().isNoContent());
        add(leaver, album, api.createReadyMemory(leaver, "PUBLIC")).andExpect(status().isNoContent());
        add(removed, album, api.createReadyMemory(removed, "PUBLIC")).andExpect(status().isNoContent());

        mockMvc.perform(as(leaver, delete("/api/albums/" + album + "/collaborators/me")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, delete("/api/albums/" + album + "/collaborators/" + removed.id())))
                .andExpect(status().isNoContent());

        assertThat(memoryIds(owner, album)).containsExactly(ownersMemory.toString());
        mockMvc.perform(as(owner, get("/api/albums/" + album))).andExpect(jsonPath("$.collaborators").isEmpty());
        mockMvc.perform(as(leaver, delete("/api/albums/" + album + "/collaborators/me")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(owner, delete("/api/albums/" + album + "/collaborators/me")))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingMemoriesFollowsRolesAndCoverFallsBackToAVisibleMemory() throws Exception {
        TestUser owner = api.signup("alb_cover_owner");
        TestUser editor = api.signup("alb_cover_editor");
        TestUser stranger = api.signup("alb_cover_stranger");
        api.follow(editor, owner);
        UUID album = album(owner, "Covers", "PUBLIC");
        collaborator(owner, album, editor, "EDITOR").andExpect(status().isNoContent());
        UUID ownersPublic = api.createReadyMemory(owner, "PUBLIC");
        UUID ownersHidden = api.createReadyMemory(owner, "ONLY_ME");
        UUID editorsMemory = api.createReadyMemory(editor, "PUBLIC");
        add(owner, album, ownersPublic, ownersHidden).andExpect(status().isNoContent());
        add(editor, album, editorsMemory).andExpect(status().isNoContent());

        mockMvc.perform(as(owner, patch("/api/albums/" + album))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMemoryId\":\"" + ownersHidden + "\"}"))
                .andExpect(status().isOk());
        // The stranger can't see the chosen cover, so they get the first memory they can see.
        assertThat(page(owner, "/api/albums/" + album).get("coverThumbUrl").asText()).contains(thumbOf(owner, ownersHidden));
        assertThat(page(stranger, "/api/albums/" + album).get("coverThumbUrl").asText()).contains(thumbOf(owner, ownersPublic));
        mockMvc.perform(as(owner, patch("/api/albums/" + album))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMemoryId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(editor, delete("/api/albums/" + album + "/memories/" + ownersPublic)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, delete("/api/albums/" + album + "/memories/" + editorsMemory)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, delete("/api/albums/" + album + "/memories/" + ownersHidden)))
                .andExpect(status().isNoContent());
        assertThat(memoryIds(owner, album)).containsExactly(ownersPublic.toString());
    }

    @Test
    void albumMemoriesArePagedInAlbumOrder() throws Exception {
        TestUser owner = api.signup("alb_page_owner");
        UUID album = album(owner, "Paged", "PUBLIC");
        List<String> added = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID memory = api.createReadyMemory(owner, "PUBLIC");
            add(owner, album, memory).andExpect(status().isNoContent());
            added.add(memory.toString());
        }

        JsonNode first = page(owner, "/api/albums/" + album + "/memories?limit=2");
        JsonNode second = page(owner, "/api/albums/" + album + "/memories?limit=2&cursor=" + first.get("nextCursor").asText());
        List<String> ids = new ArrayList<>();
        first.get("items").forEach(item -> ids.add(item.get("id").asText()));
        second.get("items").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).containsExactlyElementsOf(added);
        mockMvc.perform(as(owner, get("/api/albums/" + album + "/memories?cursor=x"))).andExpect(status().isBadRequest());
    }

    private UUID album(TestUser owner, String title, String visibility) throws Exception {
        String body = mockMvc.perform(as(owner, post("/api/albums"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("title", title, "visibility", visibility))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.viewerRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions collaborator(TestUser actor, UUID album, TestUser user, String role) throws Exception {
        return mockMvc.perform(as(actor, post("/api/albums/" + album + "/collaborators"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("userId", user.id(), "role", role))));
    }

    private ResultActions add(TestUser actor, UUID album, UUID... memories) throws Exception {
        return mockMvc.perform(as(actor, post("/api/albums/" + album + "/memories"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("memoryIds", List.of(memories)))));
    }

    private List<String> memoryIds(TestUser viewer, UUID album) throws Exception {
        List<String> ids = new ArrayList<>();
        page(viewer, "/api/albums/" + album + "/memories?limit=50").get("items")
                .forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String thumbOf(TestUser viewer, UUID memoryId) throws Exception {
        String url = api.getMemory(viewer, memoryId).at("/media/0/thumbUrl").asText();
        return url.substring(url.indexOf("thumb/"), url.indexOf('?'));
    }

    private JsonNode page(TestUser viewer, String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(viewer, get(path)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
