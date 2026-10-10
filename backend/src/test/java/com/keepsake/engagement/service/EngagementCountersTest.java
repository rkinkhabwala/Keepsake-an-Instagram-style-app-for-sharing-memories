package com.keepsake.engagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class EngagementCountersTest {

    @Test
    void fallsBackToUpdatingPostgresWhenRedisIsDown() {
        StringRedisTemplate redis = redisDown();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EngagementCounters counters = new EngagementCounters(redis, jdbc, new SimpleMeterRegistry());
        UUID memoryId = UUID.randomUUID();

        counters.likeChanged(memoryId, 1);
        counters.commentChanged(memoryId, -1);

        verify(jdbc).update("UPDATE memories SET like_count = GREATEST(like_count + ?, 0) WHERE id = ?", 1, memoryId);
        verify(jdbc).update("UPDATE memories SET comment_count = GREATEST(comment_count + ?, 0) WHERE id = ?", -1,
                memoryId);
    }

    @Test
    void readsShowStoredCountsWhenRedisIsDown() {
        EngagementCounters counters = new EngagementCounters(redisDown(), mock(JdbcTemplate.class), new SimpleMeterRegistry());

        assertThat(counters.pending(List.of(UUID.randomUUID()))).isEmpty();
    }

    private static StringRedisTemplate redisDown() {
        return mock(StringRedisTemplate.class, invocation -> {
            throw new RedisConnectionFailureException("down");
        });
    }
}
