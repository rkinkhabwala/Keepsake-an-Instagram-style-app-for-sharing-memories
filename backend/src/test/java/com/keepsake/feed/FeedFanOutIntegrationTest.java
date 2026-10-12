package com.keepsake.feed;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.feed.service.FeedFanOut;
import com.keepsake.memory.service.MemoryListService;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Spec §7 fan-out on write, with the test configuration's feed capacity of 6 and celebrity threshold of 4. */
class FeedFanOutIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired FeedFanOut fanOut;
    @Autowired MemoryListService fanOutOnRead;

    TestApi api;
    FeedTestSupport feeds;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
        feeds = new FeedTestSupport(mockMvc, objectMapper, fanOutOnRead);
    }

    @Test
    void aReadyMemoryIsCopiedIntoTheBuiltFeedsOfItsAudienceOnly() throws Exception {
        TestUser author = api.signup("fo_author");
        TestUser friend = api.signup("fo_friend");
        TestUser follower = api.signup("fo_follower");
        TestUser stranger = api.signup("fo_stranger");
        api.follow(friend, author);
        api.follow(follower, author);
        api.addCloseFriend(author, friend);
        for (TestUser user : List.of(author, friend, follower, stranger)) {
            feeds.feedIds(user, 20);
        }

        UUID everyone = api.createReadyMemory(author, "FOLLOWERS");
        UUID closeFriends = api.createReadyMemory(author, "CLOSE_FRIENDS");
        UUID onlyMe = api.createReadyMemory(author, "ONLY_ME");

        assertThat(stored(author)).contains(everyone, closeFriends, onlyMe);
        assertThat(stored(friend)).contains(everyone, closeFriends).doesNotContain(onlyMe);
        assertThat(stored(follower)).contains(everyone).doesNotContain(closeFriends, onlyMe);
        assertThat(stored(stranger)).isEmpty();
        assertThat(feeds.feedIds(friend, 20)).containsExactly(closeFriends, everyone);
    }

    @Test
    void unfollowingRemovesFannedOutMemoriesFromTheNextRequest() throws Exception {
        TestUser author = api.signup("fo_unf_author");
        TestUser reader = api.signup("fo_unf_reader");
        api.follow(reader, author);
        feeds.feedIds(reader, 20);
        UUID memoryId = api.createReadyMemory(author, "PUBLIC");
        assertThat(stored(reader)).contains(memoryId);
        assertThat(feeds.feedIds(reader, 20)).contains(memoryId);

        mockMvc.perform(as(reader, delete("/api/users/" + author.username() + "/follow")))
                .andExpect(status().isNoContent());
        assertThat(feeds.feedIds(reader, 20)).doesNotContain(memoryId);

        // Even if the entry were still in the stored feed (say, dropping the feed had failed):
        redis.opsForZSet().add("feed:" + reader.id(), memoryId.toString(), 0);
        assertThat(feeds.feedIds(reader, 20)).doesNotContain(memoryId);
    }

    @Test
    void celebritiesAreMergedInAtReadTimeAndFannedOutWhenTheyDropBelowTheThreshold() throws Exception {
        TestUser celebrity = api.signup("fo_celeb");
        List<TestUser> fans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestUser fan = api.signup("fo_celeb_fan" + i);
            api.follow(fan, celebrity);
            fans.add(fan);
        }
        fanOut.refreshCelebrities();
        TestUser fan = fans.getFirst();
        feeds.feedIds(fan, 20);
        feeds.feedIds(celebrity, 20);

        UUID memoryId = api.createReadyMemory(celebrity, "PUBLIC");

        assertThat(stored(celebrity)).contains(memoryId);
        assertThat(stored(fan)).doesNotContain(memoryId);
        assertThat(feeds.feedIds(fan, 20)).containsExactly(memoryId);

        mockMvc.perform(as(fans.getLast(), delete("/api/users/" + celebrity.username() + "/follow")))
                .andExpect(status().isNoContent());
        fanOut.refreshCelebrities();
        assertThat(stored(fan)).contains(memoryId);
        assertThat(feeds.feedIds(fan, 20)).containsExactly(memoryId);
    }

    @Test
    void aFullFeedContinuesFromTheDatabaseAndAMissingFeedIsRebuilt() throws Exception {
        TestUser author = api.signup("fo_full_author");
        TestUser reader = api.signup("fo_full_reader");
        api.follow(reader, author);
        feeds.feedIds(reader, 20);
        List<UUID> posted = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            posted.addFirst(api.createReadyMemory(author, "PUBLIC"));
        }

        assertThat(stored(reader)).containsExactlyElementsOf(posted.subList(0, 6));
        assertThat(feeds.feedIds(reader, 4)).containsExactlyElementsOf(posted);
        assertThat(feeds.feedIds(reader, 1)).containsExactlyElementsOf(posted);

        redis.delete("feed:" + reader.id());
        assertThat(feeds.feedIds(reader, 4)).containsExactlyElementsOf(posted);
        assertThat(stored(reader)).containsExactlyElementsOf(posted.subList(0, 6));
        assertThat(feeds.referenceIds(reader.id())).containsExactlyElementsOf(posted);
    }

    /** The ids in the user's stored feed, newest first. */
    private List<UUID> stored(TestUser user) {
        var ids = redis.opsForZSet().reverseRange("feed:" + user.id(), 0, -1);
        return ids.stream().filter(id -> !id.equals("0")).map(UUID::fromString).toList();
    }
}
