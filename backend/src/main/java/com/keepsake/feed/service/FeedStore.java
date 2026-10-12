package com.keepsake.feed.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Each user's fanned-out home feed in Redis: a sorted set {@code feed:{userId}} of memory ids (spec §7). Every
 * member has score 0, so the set is ordered by id; memory ids are UUIDv7 and their text sorts like Postgres
 * sorts them, which makes this the same newest-first order as the {@code ORDER BY m.id DESC} queries.
 *
 * <p>A feed exists only once it has been built: the member {@code "0"} (sorting below every id) marks a built
 * set, so an empty feed is told apart from a missing one. Fan-out adds only to feeds that exist; a missing feed
 * is rebuilt from the database on its next read. A feed keeps the newest {@code capacity} ids.
 */
@Component
public class FeedStore {

    static final String BUILT = "0";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int FAN_OUT_BATCH = 500;

    /** ARGV[1] memory id, ARGV[2] capacity: add to each existing feed and trim it, keeping the BUILT marker. */
    private static final RedisScript<Long> FAN_OUT = RedisScript.of("""
            local trimTo = -(tonumber(ARGV[2]) + 1)
            for _, key in ipairs(KEYS) do
              if redis.call('EXISTS', key) == 1 then
                redis.call('ZADD', key, 0, ARGV[1])
                redis.call('ZREMRANGEBYRANK', key, 1, trimTo)
              end
            end
            return 0
            """, Long.class);

    /** Redis could not be reached; the caller falls back to the database. */
    public static class UnavailableException extends RuntimeException {
        UnavailableException(Throwable cause) {
            super(cause);
        }
    }

    private final StringRedisTemplate redis;
    private final int capacity;

    public FeedStore(StringRedisTemplate redis, @Value("${keepsake.feed.capacity:800}") int capacity) {
        this.redis = redis;
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    /** True when the user's feed is built; also extends its lifetime, since it is being read. */
    boolean touch(UUID userId) {
        return call(() -> Boolean.TRUE.equals(redis.expire(key(userId), TTL)));
    }

    /**
     * Starts a rebuild by creating the feed with only the BUILT marker, so fan-out from now on lands in it;
     * memories that became visible earlier are in the database query the caller then adds with {@link #addAll}.
     */
    void create(UUID userId) {
        call(() -> {
            redis.opsForZSet().add(key(userId), BUILT, 0);
            return redis.expire(key(userId), TTL);
        });
    }

    void addAll(UUID userId, Collection<UUID> memoryIds) {
        if (memoryIds.isEmpty()) {
            return;
        }
        Set<TypedTuple<String>> members = new java.util.HashSet<>();
        memoryIds.forEach(id -> members.add(TypedTuple.of(id.toString(), 0.0)));
        call(() -> redis.opsForZSet().add(key(userId), members));
    }

    /** Ids strictly older than {@code below} (or the newest, when null), newest first. */
    List<String> page(UUID userId, String below, int count) {
        Range<String> range = Range.of(Range.Bound.exclusive(BUILT),
                below == null ? Range.Bound.unbounded() : Range.Bound.exclusive(below));
        return call(() -> {
            Set<String> ids = redis.opsForZSet().reverseRangeByLex(key(userId), range, Limit.limit().count(count));
            return ids == null ? List.<String>of() : new ArrayList<>(ids);
        });
    }

    /** How many memory ids the feed holds. */
    long size(UUID userId) {
        return call(() -> {
            Long size = redis.opsForZSet().zCard(key(userId));
            return size == null || size == 0 ? 0L : size - 1;
        });
    }

    /** Adds the memory to the feeds of these users that are built. */
    void fanOut(Collection<UUID> userIds, UUID memoryId) {
        List<String> keys = userIds.stream().map(FeedStore::key).toList();
        for (int start = 0; start < keys.size(); start += FAN_OUT_BATCH) {
            List<String> batch = keys.subList(start, Math.min(keys.size(), start + FAN_OUT_BATCH));
            call(() -> redis.execute(FAN_OUT, batch, memoryId.toString(), String.valueOf(capacity)));
        }
    }

    /**
     * Drops these users' feeds once the current transaction commits, so their next read rebuilds them from the
     * database. Used when follows, blocks, or close friends change which memories belong in a feed.
     */
    public void invalidateAfterCommit(UUID... userIds) {
        invalidateAfterCommit(List.of(userIds));
    }

    public void invalidateAfterCommit(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream().map(FeedStore::key).toList();
        Runnable drop = () -> {
            try {
                redis.delete(keys);
            } catch (DataAccessException unavailable) {
                // The read path re-checks every entry, so a stale feed still never shows a hidden memory.
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    drop.run();
                }
            });
        } else {
            drop.run();
        }
    }

    static String key(UUID userId) {
        return "feed:" + userId;
    }

    private static <T> T call(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException unavailable) {
            throw new UnavailableException(unavailable);
        }
    }
}
