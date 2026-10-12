package com.keepsake.notification;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.notification.service.OnThisDayNotifier;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Phase 3 notifications: what is recorded, for whom, how it is listed, and live delivery over STOMP. */
class NotificationIntegrationTest extends IntegrationTestBase {

    private static final Duration ARRIVAL = Duration.ofSeconds(10);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired OnThisDayNotifier onThisDay;
    @LocalServerPort int port;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void likesOnAMemoryAreGroupedUntilRead() throws Exception {
        TestUser author = api.signup("nt_like_author");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        TestUser first = api.signup("nt_like_b");
        TestUser again = api.signup("nt_like_d");
        TestUser last = api.signup("nt_like_c");
        like(first, memoryId);
        like(again, memoryId);
        // Unliking and liking again does not count the same person twice, and your own like is not news.
        mockMvc.perform(as(again, delete("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
        like(again, memoryId);
        like(author, memoryId);
        like(last, memoryId);

        // Events for one recipient are handled in order, so once the last like shows, the others were handled.
        TestApi.await(ARRIVAL, () -> items(author).size() == 1
                && items(author).getFirst().at("/actor/username").asText().equals(last.username()));
        JsonNode grouped = items(author).getFirst();
        assertThat(grouped.get("type").asText()).isEqualTo("LIKE");
        assertThat(grouped.get("othersCount").asInt()).isEqualTo(2);
        assertThat(grouped.get("memoryId").asText()).isEqualTo(memoryId.toString());
        assertThat(grouped.get("memoryThumbUrl").asText()).isNotBlank();
        assertThat(unread(author)).isEqualTo(1);

        mockMvc.perform(as(author, post("/api/notifications/read"))).andExpect(status().isNoContent());
        assertThat(unread(author)).isZero();
        TestUser late = api.signup("nt_like_e");
        like(late, memoryId);
        TestApi.await(ARRIVAL, () -> items(author).size() == 2);
        assertThat(items(author).getFirst().get("othersCount").asInt()).isZero();
        assertThat(items(author).getFirst().get("read").asBoolean()).isFalse();
        assertThat(items(author).getLast().get("read").asBoolean()).isTrue();
    }

    @Test
    void commentsRepliesFollowsRequestsAndTagsNotifyTheRightPeople() throws Exception {
        TestUser author = api.signup("nt_kinds_author");
        TestUser commenter = api.signup("nt_kinds_commenter");
        TestUser follower = api.signup("nt_kinds_follower");
        TestUser locked = api.signup("nt_kinds_locked");
        api.makePrivate(locked);
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");

        UUID commentId = comment(commenter, memoryId, null, "What a day that was");
        comment(author, memoryId, commentId, "It really was");
        api.follow(follower, author);
        api.requestFollow(author, locked);
        UUID taggedMemory = api.createReadyMemory(author, "PUBLIC", List.of(commenter.username()));

        TestApi.await(ARRIVAL, () -> types(author).equals(List.of("FOLLOW", "COMMENT")));
        assertThat(items(author).getLast().get("commentPreview").asText()).isEqualTo("What a day that was");
        assertThat(items(author).getFirst().at("/actor/username").asText()).isEqualTo(follower.username());
        TestApi.await(ARRIVAL, () -> types(commenter).equals(List.of("TAGGED", "COMMENT")));
        assertThat(items(commenter).getFirst().get("memoryId").asText()).isEqualTo(taggedMemory.toString());
        assertThat(items(commenter).getLast().get("commentPreview").asText()).isEqualTo("It really was");
        TestApi.await(ARRIVAL, () -> types(locked).equals(List.of("FOLLOW_REQUEST")));
    }

    @Test
    void nobodyHearsAboutHiddenMemoriesOrPeopleTheyBlocked() throws Exception {
        TestUser author = api.signup("nt_hide_author");
        TestUser fan = api.signup("nt_hide_fan");
        TestUser other = api.signup("nt_hide_other");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        api.createReadyMemory(author, "ONLY_ME", List.of(other.username()));

        like(fan, memoryId);
        TestApi.await(ARRIVAL, () -> unread(author) == 1);

        api.block(author, fan);
        assertThat(items(author)).isEmpty();
        assertThat(unread(author)).isZero();

        // Tagged in a memory they cannot see, then a visible one: only the second arrives.
        api.createReadyMemory(author, "PUBLIC", List.of(other.username()));
        TestApi.await(ARRIVAL, () -> types(other).size() == 1);
        assertThat(types(other)).containsExactly("TAGGED");
    }

    @Test
    void aNotificationReachesTheRecipientsOpenSocketWithinTwoSeconds() throws Exception {
        TestUser author = api.signup("nt_live_author");
        TestUser fan = api.signup("nt_live_fan");
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        StompSession session = connect(author.accessToken());
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((JsonNode) payload);
            }
        });
        Thread.sleep(300);

        long start = System.nanoTime();
        like(fan, memoryId);
        JsonNode notification = received.poll(2, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(notification).as("no notification within 2 s").isNotNull();
        assertThat(elapsedMillis).isLessThan(2000);
        assertThat(notification.get("type").asText()).isEqualTo("LIKE");
        assertThat(notification.at("/actor/username").asText()).isEqualTo(fan.username());
        session.disconnect();
    }

    @Test
    void theSocketNeedsAValidTokenAndOnlyServesTheUsersOwnQueue() throws Exception {
        assertThatThrownBy(() -> connect("not-a-token")).isInstanceOf(Exception.class);

        TestUser user = api.signup("nt_socket_user");
        StompSession session = connect(user.accessToken());
        session.subscribe("/queue/notifications", new StompSessionHandlerAdapter() {
        });
        TestApi.await(Duration.ofSeconds(5), () -> !session.isConnected());
    }

    @Test
    void timeZonesAreCapturedAtSignupAndLoginWhenValid() throws Exception {
        signupWithZone("nt_zone_tokyo", "Asia/Tokyo");
        assertThat(zoneOf("nt_zone_tokyo")).isEqualTo("Asia/Tokyo");
        signupWithZone("nt_zone_bad", "Mars/Olympus_Mons");
        assertThat(zoneOf("nt_zone_bad")).isNull();

        login("nt_zone_bad", "Europe/Paris");
        assertThat(zoneOf("nt_zone_bad")).isEqualTo("Europe/Paris");
        login("nt_zone_bad", "not a zone");
        assertThat(zoneOf("nt_zone_bad")).isEqualTo("Europe/Paris");
    }

    @Test
    void onThisDayArrivesOnceFromNineInTheMorningLocalTime() throws Exception {
        TestUser tokyo = signupWithZone("nt_otd_tokyo", "Asia/Tokyo");
        TestUser newYork = api.signup("nt_otd_default");
        UUID tokyoMemory = memoryOn(tokyo, "2023-10-09");
        memoryOn(newYork, "2021-10-09");

        // 2026-10-09 08:59 in Tokyo; 2026-10-08 19:59 in New York.
        onThisDay.sendDue(Instant.parse("2026-10-08T23:59:00Z"));
        assertThat(types(tokyo)).isEmpty();
        onThisDay.sendDue(Instant.parse("2026-10-09T00:00:00Z"));
        TestApi.await(ARRIVAL, () -> types(tokyo).equals(List.of("ON_THIS_DAY")));
        assertThat(items(tokyo).getFirst().get("memoryId").asText()).isEqualTo(tokyoMemory.toString());
        assertThat(items(tokyo).getFirst().get("actor").isNull()).isTrue();

        // 08:59 and then 09:00 on 2026-10-09 in New York (UTC-4); a repeated run sends nothing more.
        onThisDay.sendDue(Instant.parse("2026-10-09T12:59:00Z"));
        assertThat(types(newYork)).isEmpty();
        onThisDay.sendDue(Instant.parse("2026-10-09T13:00:00Z"));
        onThisDay.sendDue(Instant.parse("2026-10-09T15:00:00Z"));
        assertThat(types(newYork)).containsExactly("ON_THIS_DAY");
        assertThat(types(tokyo)).containsExactly("ON_THIS_DAY");
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private void like(TestUser user, UUID memoryId) throws Exception {
        mockMvc.perform(as(user, put("/api/memories/" + memoryId + "/like"))).andExpect(status().isNoContent());
    }

    private UUID comment(TestUser user, UUID memoryId, UUID parentId, String body) throws Exception {
        Map<String, Object> request = parentId == null ? Map.of("body", body) : Map.of("body", body, "parentId", parentId);
        String response = mockMvc.perform(as(user, post("/api/memories/" + memoryId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID memoryOn(TestUser author, String occurredOn) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", com.keepsake.support.TestImages.jpeg(32, 32));
        UUID memoryId = api.createMemory(author, List.of(uploadId),
                Map.of("visibility", "ONLY_ME", "occurredOn", occurredOn));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId;
    }

    private List<JsonNode> items(TestUser user) {
        try {
            JsonNode page = objectMapper.readTree(mockMvc.perform(as(user, get("/api/notifications")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            List<JsonNode> items = new ArrayList<>();
            page.get("items").forEach(items::add);
            return items;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> types(TestUser user) {
        return items(user).stream().map(item -> item.get("type").asText()).toList();
    }

    private long unread(TestUser user) {
        try {
            return objectMapper.readTree(mockMvc.perform(as(user, get("/api/notifications/unread-count")))
                    .andReturn().getResponse().getContentAsString()).get("count").asLong();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private TestUser signupWithZone(String username, String timeZone) throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup")
                        .with(request -> {
                            request.setRemoteAddr("198.19.0." + (Math.abs(username.hashCode()) % 250 + 1));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("username", username, "email", username + "@example.test",
                                "password", "Keepsake-password-2026", "displayName", username,
                                "timeZone", timeZone))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new TestUser(UUID.fromString(json.at("/user/id").asText()), username,
                json.get("accessToken").asText());
    }

    private void login(String username, String timeZone) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.19.1." + (Math.abs(timeZone.hashCode()) % 250 + 1));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("identifier", username, "password", "Keepsake-password-2026",
                                "timeZone", timeZone))))
                .andExpect(status().isOk());
    }

    private String zoneOf(String username) {
        return jdbc.queryForObject("SELECT time_zone FROM users WHERE username = ?", String.class, username);
    }
}
