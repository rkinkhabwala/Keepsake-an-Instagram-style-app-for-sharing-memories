package com.keepsake.notification.service;

import com.keepsake.common.events.Outbox;
import com.keepsake.common.events.Topics;
import com.keepsake.notification.entity.NotificationType;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the social and memory events that notifications are made from, through the outbox so an event is
 * sent only if the action that caused it commits. Whether the recipient may be told is decided when the event is
 * consumed ({@link NotificationService#record}).
 */
@Component
public class NotificationEvents {

    /** @param eventId identifies the event, so a redelivered one cannot notify twice */
    public record NotificationEvent(
            UUID eventId,
            NotificationType type,
            UUID recipientId,
            UUID actorId,
            UUID memoryId,
            UUID commentId
    ) {
    }

    private final Outbox outbox;

    public NotificationEvents(Outbox outbox) {
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void liked(UUID actorId, UUID authorId, UUID memoryId) {
        publish(Topics.MEMORY_EVENTS, NotificationType.LIKE, authorId, actorId, memoryId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commented(UUID actorId, UUID recipientId, UUID memoryId, UUID commentId) {
        publish(Topics.MEMORY_EVENTS, NotificationType.COMMENT, recipientId, actorId, memoryId, commentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tagged(UUID authorId, Collection<UUID> taggedUserIds, UUID memoryId) {
        taggedUserIds.forEach(userId ->
                publish(Topics.MEMORY_EVENTS, NotificationType.TAGGED, userId, authorId, memoryId, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void followed(UUID followerId, UUID followeeId, boolean pending) {
        publish(Topics.SOCIAL_EVENTS, pending ? NotificationType.FOLLOW_REQUEST : NotificationType.FOLLOW,
                followeeId, followerId, null, null);
    }

    private void publish(
            String topic,
            NotificationType type,
            UUID recipientId,
            UUID actorId,
            UUID memoryId,
            UUID commentId
    ) {
        outbox.publish(topic, recipientId.toString(),
                new NotificationEvent(UUID.randomUUID(), type, recipientId, actorId, memoryId, commentId));
    }
}
