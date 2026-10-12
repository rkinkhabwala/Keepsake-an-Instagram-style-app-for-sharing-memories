package com.keepsake.feed.service;

import com.keepsake.memory.entity.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fan-out on write (spec §7): copies a READY memory's id into the feed of everyone it is meant for, i.e. the
 * author, plus accepted followers (only those who are close friends for CLOSE_FRIENDS, nobody for ONLY_ME).
 * Authors above the celebrity threshold reach only their own feed. This is a pre-filter; the read path
 * re-checks visibility, so a later unfollow or visibility change is respected anyway, and an id added before
 * its memory is READY (or by a transaction that rolls back) is never shown.
 */
@Component
public class FeedFanOut {

    private static final Logger log = LoggerFactory.getLogger(FeedFanOut.class);

    private final JdbcTemplate jdbc;
    private final FeedStore store;
    private final CelebrityAccounts celebrities;

    public FeedFanOut(JdbcTemplate jdbc, FeedStore store, CelebrityAccounts celebrities) {
        this.jdbc = jdbc;
        this.store = store;
        this.celebrities = celebrities;
    }

    public void fanOut(UUID authorId, Visibility visibility, UUID memoryId) {
        List<UUID> recipients = new ArrayList<>();
        recipients.add(authorId);
        if (!celebrities.contains(authorId)) {
            recipients.addAll(audience(authorId, visibility.name()));
        }
        try {
            store.fanOut(recipients, memoryId);
        } catch (FeedStore.UnavailableException unavailable) {
            // Never fail the memory over its feeds; feeds built before the outage miss it until rebuilt.
            log.warn("Feed store unavailable; memory {} was not fanned out", memoryId, unavailable);
        }
    }

    private void fanOut(UUID memoryId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT author_id, visibility FROM memories
                WHERE id = ? AND status = 'READY' AND deleted_at IS NULL
                """, memoryId);
        if (rows.isEmpty()) {
            return;
        }
        fanOut((UUID) rows.getFirst().get("author_id"),
                Visibility.valueOf((String) rows.getFirst().get("visibility")), memoryId);
    }

    /**
     * Refreshes the celebrity set. An account that drops below the threshold has its recent memories fanned out
     * now, since feeds will no longer merge them in at read time.
     */
    @Scheduled(fixedDelayString = "${keepsake.feed.celebrity-refresh:PT1M}")
    public void refreshCelebrities() {
        for (UUID authorId : celebrities.refresh()) {
            jdbc.queryForList("""
                    SELECT id FROM memories WHERE author_id = ? AND status = 'READY' AND deleted_at IS NULL
                    ORDER BY id DESC LIMIT ?
                    """, UUID.class, authorId, store.capacity()).forEach(this::fanOut);
        }
    }

    private List<UUID> audience(UUID authorId, String visibility) {
        return switch (visibility) {
            case "ONLY_ME" -> List.of();
            case "CLOSE_FRIENDS" -> jdbc.queryForList("""
                    SELECT f.follower_id FROM follows f
                    JOIN close_friends c ON c.owner_id = f.followee_id AND c.friend_id = f.follower_id
                    WHERE f.followee_id = ? AND f.status = 'ACCEPTED'
                    """, UUID.class, authorId);
            default -> jdbc.queryForList(
                    "SELECT follower_id FROM follows WHERE followee_id = ? AND status = 'ACCEPTED'",
                    UUID.class, authorId);
        };
    }
}
