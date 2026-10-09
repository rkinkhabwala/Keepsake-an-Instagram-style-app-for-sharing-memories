package com.keepsake.feed.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Accounts with more followers than the fan-out threshold (spec §7: 10k). Their memories are not copied into
 * followers' feeds; the feed merges them in at read time. Refreshed periodically by {@link FeedFanOut}.
 */
@Component
public class CelebrityAccounts {

    private final JdbcTemplate jdbc;
    private final int threshold;
    private volatile Set<UUID> current = Set.of();

    public CelebrityAccounts(JdbcTemplate jdbc, @Value("${keepsake.feed.celebrity-threshold:10000}") int threshold) {
        this.jdbc = jdbc;
        this.threshold = threshold;
    }

    public boolean contains(UUID userId) {
        return current.contains(userId);
    }

    public Set<UUID> all() {
        return current;
    }

    /** Recounts followers and returns the accounts that dropped below the threshold. */
    Set<UUID> refresh() {
        List<UUID> above = jdbc.queryForList("""
                SELECT followee_id FROM follows WHERE status = 'ACCEPTED'
                GROUP BY followee_id HAVING COUNT(*) > ?
                """, UUID.class, threshold);
        Set<UUID> next = Set.copyOf(above);
        Set<UUID> demoted = new HashSet<>(current);
        demoted.removeAll(next);
        current = next;
        return demoted;
    }
}
