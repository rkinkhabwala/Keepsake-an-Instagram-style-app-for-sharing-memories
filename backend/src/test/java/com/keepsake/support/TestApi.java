package com.keepsake.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Drives the real HTTP API for integration tests: sign up, follow, upload, and create memories. */
public class TestApi {

    /** Shared across test classes so every signup gets its own client IP and stays under the signup limit. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestApi(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public record TestUser(UUID id, String username, String accessToken) {
    }

    public TestUser signup(String username) throws Exception {
        int ip = NEXT_IP.getAndIncrement();
        String body = mockMvc.perform(post("/api/auth/signup")
                        .with(request -> {
                            request.setRemoteAddr("198.18." + (ip / 250) + "." + (ip % 250 + 1));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.test",
                                "password", "Keepsake-password-2026",
                                "displayName", username))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new TestUser(UUID.fromString(json.at("/user/id").asText()), username, json.get("accessToken").asText());
    }

    public void makePrivate(TestUser user) throws Exception {
        mockMvc.perform(as(user, patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":true}"))
                .andExpect(status().isOk());
    }

    /** Follows and, for a private account, has the owner accept the request. */
    public void follow(TestUser follower, TestUser followee) throws Exception {
        requestFollow(follower, followee);
        mockMvc.perform(as(followee, post("/api/follow-requests/" + follower.id() + "/accept")));
    }

    /** Follows without accepting, leaving a private account's request PENDING. */
    public void requestFollow(TestUser follower, TestUser followee) throws Exception {
        mockMvc.perform(as(follower, post("/api/users/" + followee.username() + "/follow")))
                .andExpect(status().isOk());
    }

    public void addCloseFriend(TestUser owner, TestUser friend) throws Exception {
        mockMvc.perform(as(owner, put("/api/close-friends/" + friend.id())))
                .andExpect(status().isNoContent());
    }

    /** Requests an upload URL and PUTs the bytes to storage, as the web client does. */
    public UUID upload(TestUser owner, String contentType, byte[] bytes) throws Exception {
        UUID uploadId = requestUpload(owner, contentType, bytes.length);
        assertThat(putUpload(owner, uploadId, bytes)).isEqualTo(200);
        return uploadId;
    }

    private final Map<UUID, JsonNode> uploadSlots = new java.util.concurrent.ConcurrentHashMap<>();

    /** Requests an upload URL without uploading anything yet. */
    public UUID requestUpload(TestUser owner, String contentType, long sizeBytes) throws Exception {
        String body = mockMvc.perform(as(owner, post("/api/media/upload-urls"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of("contentType", contentType, "sizeBytes", sizeBytes)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode slot = objectMapper.readTree(body).get(0);
        UUID uploadId = UUID.fromString(slot.get("uploadId").asText());
        uploadSlots.put(uploadId, slot);
        return uploadId;
    }

    public int putUpload(TestUser owner, UUID uploadId, byte[] bytes) throws Exception {
        JsonNode slot = uploadSlots.get(uploadId);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(slot.get("url").asText()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        slot.get("headers").fields().forEachRemaining(header ->
                request.header(header.getKey(), header.getValue().asText()));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Creates a memory and returns its id; {@code fields} are merged into the request body. */
    public UUID createMemory(TestUser author, List<UUID> uploadIds, Map<String, Object> fields) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(fields);
        body.put("uploadIds", uploadIds);
        body.putIfAbsent("visibility", "PUBLIC");
        String response = mockMvc.perform(as(author, post("/api/memories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    /** Creates a memory from one generated photo and waits until processing finishes. */
    public UUID createReadyMemory(TestUser author, String visibility) throws Exception {
        UUID uploadId = upload(author, "image/jpeg", TestImages.jpeg(64, 48));
        UUID memoryId = createMemory(author, List.of(uploadId), Map.of("visibility", visibility));
        awaitStatus(author, memoryId, "READY");
        return memoryId;
    }

    public JsonNode getMemory(TestUser viewer, UUID memoryId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/memories/" + memoryId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    public JsonNode awaitStatus(TestUser author, UUID memoryId, String expected) throws Exception {
        JsonNode[] latest = new JsonNode[1];
        await(Duration.ofSeconds(30), () -> {
            try {
                latest[0] = getMemory(author, memoryId);
                return latest[0].get("status").asText().equals(expected);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        return latest[0];
    }

    public static void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            Thread.sleep(50);
        }
    }

    public static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + user.accessToken());
    }

    public String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
