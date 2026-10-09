package com.keepsake.engagement.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Like and comment counts with Redis in front of Postgres (spec §7). A change adds a pending delta in Redis after
 * its transaction commits and marks the memory dirty; reads add pending deltas to the database columns. Every few
 * seconds a flush snapshots the dirty memories and recomputes their columns from the likes and comments rows,
 * which stay the source of truth. Recomputing (rather than adding deltas) makes a flush safe to repeat after a
 * crash. If Redis is unavailable, changes fall back to updating the columns directly and reads use the columns.
 */
@Component
public class EngagementCounters {

    private static final Logger log = LoggerFactory.getLogger(EngagementCounters.class);

    static final String LIKES = "keepsake:counts:likes";
    static final String COMMENTS = "keepsake:counts:comments";
    static final String DIRTY = "keepsake:counts:dirty";
    static final String FLUSHING = ":flushing";

    /**
     * Moves the live keys to their ":flushing" names in one step, unless a previous flush left a snapshot behind
     * (then that one is finished first). Returns 1 if there is a snapshot to process.
     */
    private static final DefaultRedisScript<Long> SNAPSHOT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[4]) == 1 then return 1 end
            if redis.call('EXISTS', KEYS[3]) == 0 then return 0 end
            for i = 1, 3 do
              if redis.call('EXISTS', KEYS[i]) == 1 then redis.call('RENAME', KEYS[i], KEYS[i + 3]) end
            end
            return 1
            """, Long.class);

    public record Counts(int likes, int comments) {
    }

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final Timer flushTimer;

    public EngagementCounters(StringRedisTemplate redis, JdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.flushTimer = Timer.builder("keepsake.counters.flush")
                .description("Time to recompute counts for memories with pending likes or comments")
                .register(meterRegistry);
    }

    public void likeChanged(UUID memoryId, int delta) {
        afterCommit(memoryId, LIKES, delta);
    }

    public void commentChanged(UUID memoryId, int delta) {
        afterCommit(memoryId, COMMENTS, delta);
    }

    /** Pending (not yet flushed) deltas for the memories; empty if Redis cannot be reached. */
    public Map<UUID, Counts> pending(Collection<UUID> memoryIds) {
        if (memoryIds.isEmpty()) {
            return Map.of();
        }
        List<String> fields = memoryIds.stream().map(UUID::toString).toList();
        try {
            List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
                byte[][] keys = fields.stream().map(String::getBytes).toArray(byte[][]::new);
                for (String hash : List.of(LIKES, LIKES + FLUSHING, COMMENTS, COMMENTS + FLUSHING)) {
                    connection.hashCommands().hMGet(hash.getBytes(), keys);
                }
                return null;
            });
            Map<UUID, Counts> pending = new HashMap<>();
            int index = 0;
            for (UUID memoryId : memoryIds) {
                int likes = number(results.get(0), index) + number(results.get(1), index);
                int comments = number(results.get(2), index) + number(results.get(3), index);
                if (likes != 0 || comments != 0) {
                    pending.put(memoryId, new Counts(likes, comments));
                }
                index++;
            }
            return pending;
        } catch (DataAccessException unavailable) {
            log.warn("Redis unavailable; showing stored counts without pending changes", unavailable);
            return Map.of();
        }
    }

    @Scheduled(fixedDelayString = "${keepsake.counters.flush-interval:PT2S}")
    public void flush() {
        try {
            flushTimer.record(this::flushSnapshot);
        } catch (DataAccessException unavailable) {
            log.warn("Counter flush skipped: Redis or the database is unavailable", unavailable);
        }
    }

    private void flushSnapshot() {
        Long hasSnapshot = redis.execute(SNAPSHOT,
                List.of(LIKES, COMMENTS, DIRTY, LIKES + FLUSHING, COMMENTS + FLUSHING, DIRTY + FLUSHING));
        if (hasSnapshot == null || hasSnapshot == 0) {
            return;
        }
        Set<String> dirty = redis.opsForSet().members(DIRTY + FLUSHING);
        if (dirty != null && !dirty.isEmpty()) {
            recompute(dirty.stream().map(UUID::fromString).collect(Collectors.toSet()));
        }
        redis.delete(List.of(LIKES + FLUSHING, COMMENTS + FLUSHING, DIRTY + FLUSHING));
    }

    /**
     * Sets the columns from the source rows now and drops those memories' pending deltas, e.g. after an account
     * deletion removed likes and comments. The memories stay dirty, so the next flush recomputes them again in
     * case a change raced with this.
     */
    public void reconcileNow(Collection<UUID> memoryIds) {
        if (memoryIds.isEmpty()) {
            return;
        }
        recompute(memoryIds);
        try {
            Object[] fields = memoryIds.stream().map(UUID::toString).toArray();
            for (String hash : List.of(LIKES, COMMENTS, LIKES + FLUSHING, COMMENTS + FLUSHING)) {
                redis.opsForHash().delete(hash, fields);
            }
            redis.opsForSet().add(DIRTY, memoryIds.stream().map(UUID::toString).toArray(String[]::new));
        } catch (DataAccessException unavailable) {
            log.warn("Redis unavailable while reconciling counts; columns were still recomputed", unavailable);
        }
    }

    private void recompute(Collection<UUID> memoryIds) {
        jdbc.update("""
                UPDATE memories m SET
                    like_count = (SELECT COUNT(*) FROM likes l WHERE l.memory_id = m.id),
                    comment_count = (SELECT COUNT(*) FROM comments c WHERE c.memory_id = m.id AND c.deleted_at IS NULL)
                WHERE m.id = ANY (?)
                """, (Object) memoryIds.toArray(UUID[]::new));
    }

    private void afterCommit(UUID memoryId, String hash, int delta) {
        Runnable apply = () -> {
            try {
                redis.executePipelined((RedisCallback<Object>) connection -> {
                    connection.hashCommands().hIncrBy(hash.getBytes(), memoryId.toString().getBytes(), delta);
                    connection.setCommands().sAdd(DIRTY.getBytes(), memoryId.toString().getBytes());
                    return null;
                });
            } catch (DataAccessException unavailable) {
                log.warn("Redis unavailable; updating the count for memory {} directly", memoryId, unavailable);
                String column = hash.equals(LIKES) ? "like_count" : "comment_count";
                jdbc.update("UPDATE memories SET " + column + " = GREATEST(" + column + " + ?, 0) WHERE id = ?",
                        delta, memoryId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    apply.run();
                }
            });
        } else {
            apply.run();
        }
    }

    private static int number(Object hmgetResult, int index) {
        if (!(hmgetResult instanceof List<?> values) || values.get(index) == null) {
            return 0;
        }
        Object value = values.get(index);
        return Integer.parseInt(value instanceof byte[] bytes ? new String(bytes) : value.toString());
    }
}
