package com.keepsake.notification.repository;

import com.keepsake.common.id.UuidV7;
import com.keepsake.memory.service.MemoryAccessPolicy;
import com.keepsake.notification.entity.NotificationType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Notifications, read through the same rules as everything else: a notification about a memory is listed only
 * while {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL} lets the recipient see it, and none from an actor they
 * are in a block with, or about a deleted comment.
 */
@Repository
public class NotificationRepository {

    public record NotificationRow(
            UUID id,
            NotificationType type,
            UUID actorId,
            int actorCount,
            UUID memoryId,
            String commentBody,
            boolean read,
            Instant updatedAt
    ) {
    }

    private static final String LISTABLE = """
            FROM notifications n
            LEFT JOIN memories m ON m.id = n.memory_id
            LEFT JOIN users a ON a.id = m.author_id
            LEFT JOIN comments c ON c.id = n.comment_id
            WHERE n.recipient_id = :viewerId
              AND (n.actor_id IS NULL OR NOT EXISTS (
                    SELECT 1 FROM blocks nb
                    WHERE (nb.blocker_id = :viewerId AND nb.blocked_id = n.actor_id)
                       OR (nb.blocker_id = n.actor_id AND nb.blocked_id = :viewerId)))
              AND (n.comment_id IS NULL OR c.deleted_at IS NULL)
              AND (n.memory_id IS NULL OR """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + ")\n";

    private static final RowMapper<NotificationRow> ROW = (rs, rowNum) -> new NotificationRow(
            rs.getObject("id", UUID.class),
            NotificationType.valueOf(rs.getString("type")),
            rs.getObject("actor_id", UUID.class),
            rs.getInt("actor_count"),
            rs.getObject("memory_id", UUID.class),
            rs.getString("comment_body"),
            rs.getTimestamp("read_at") != null,
            rs.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest activity first, strictly after the (updatedAt, id) cursor. */
    public List<NotificationRow> findVisible(UUID viewerId, Instant before, UUID beforeId, int limit) {
        return jdbc.query("SELECT n.*, c.body AS comment_body " + LISTABLE + """
                  AND (n.updated_at, n.id) < (:before, :beforeId)
                ORDER BY n.updated_at DESC, n.id DESC
                LIMIT :limit
                """, Map.of("viewerId", viewerId, "before", Timestamp.from(before), "beforeId", beforeId,
                "limit", limit), ROW);
    }

    public Optional<NotificationRow> findVisible(UUID viewerId, UUID notificationId) {
        return jdbc.query("SELECT n.*, c.body AS comment_body " + LISTABLE + " AND n.id = :id",
                Map.of("viewerId", viewerId, "id", notificationId), ROW).stream().findFirst();
    }

    public long countUnread(UUID viewerId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + LISTABLE + " AND n.read_at IS NULL",
                Map.of("viewerId", viewerId), Long.class);
        return count == null ? 0 : count;
    }

    public void markAllRead(UUID recipientId, Instant now) {
        jdbc.update("UPDATE notifications SET read_at = :now WHERE recipient_id = :recipientId AND read_at IS NULL",
                Map.of("recipientId", recipientId, "now", Timestamp.from(now)));
    }

    /**
     * Adds the liker to the recipient's unread like notification for the memory, creating it if needed.
     *
     * @return the notification, unless this person's like was already counted in it
     */
    public Optional<UUID> addLike(UUID recipientId, UUID actorId, UUID memoryId, Instant now) {
        Map<String, Object> params = Map.of("id", UuidV7.generate(), "recipientId", recipientId,
                "actorId", actorId, "memoryId", memoryId, "now", Timestamp.from(now));
        UUID id = jdbc.queryForObject("""
                INSERT INTO notifications (id, recipient_id, actor_id, type, memory_id, actor_count, created_at,
                                           updated_at)
                VALUES (:id, :recipientId, :actorId, 'LIKE', :memoryId, 0, :now, :now)
                ON CONFLICT (recipient_id, memory_id) WHERE type = 'LIKE' AND read_at IS NULL
                DO UPDATE SET updated_at = notifications.updated_at
                RETURNING id
                """, params, UUID.class);
        int added = jdbc.update("""
                INSERT INTO notification_actors (notification_id, actor_id) VALUES (:id, :actorId)
                ON CONFLICT DO NOTHING
                """, Map.of("id", id, "actorId", actorId));
        if (added == 0) {
            return Optional.empty();
        }
        jdbc.update("""
                UPDATE notifications SET actor_id = :actorId, actor_count = actor_count + 1, updated_at = :now
                WHERE id = :id
                """, Map.of("id", id, "actorId", actorId, "now", Timestamp.from(now)));
        return Optional.of(id);
    }

    /** @return the new notification, or empty if this event was already recorded */
    public Optional<UUID> add(
            UUID recipientId,
            NotificationType type,
            UUID actorId,
            UUID memoryId,
            UUID commentId,
            UUID sourceEventId,
            Instant now
    ) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", UuidV7.generate());
        params.put("recipientId", recipientId);
        params.put("type", type.name());
        params.put("actorId", actorId);
        params.put("memoryId", memoryId);
        params.put("commentId", commentId);
        params.put("sourceEventId", sourceEventId);
        params.put("now", Timestamp.from(now));
        return jdbc.queryForList("""
                INSERT INTO notifications (id, recipient_id, actor_id, type, memory_id, comment_id, source_event_id,
                                           created_at, updated_at)
                VALUES (:id, :recipientId, :actorId, :type, :memoryId, :commentId, :sourceEventId, :now, :now)
                ON CONFLICT (recipient_id, source_event_id) DO NOTHING
                RETURNING id
                """, params, UUID.class).stream().findFirst();
    }
}
