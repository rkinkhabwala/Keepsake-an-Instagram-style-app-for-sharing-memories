package com.keepsake.media.service;

import com.keepsake.common.events.Outbox;
import com.keepsake.common.events.Topics;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues media processing through the outbox, so a job is sent to Kafka only if the transaction that claimed
 * its uploads commits.
 */
@Component
public class MediaJobs {

    /** @param submittedAt when the job was queued, to measure queue lag */
    public record MemoryMediaJob(UUID memoryId, List<UUID> uploadIds, Instant submittedAt) {
    }

    public record AvatarMediaJob(UUID userId, UUID uploadId, Instant submittedAt) {
    }

    private final Outbox outbox;
    private final Clock clock;

    public MediaJobs(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void submitMemory(UUID memoryId, List<UUID> uploadIds) {
        outbox.publish(Topics.MEMORY_MEDIA, memoryId.toString(),
                new MemoryMediaJob(memoryId, List.copyOf(uploadIds), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void submitAvatar(UUID userId, UUID uploadId) {
        outbox.publish(Topics.AVATAR_MEDIA, userId.toString(), new AvatarMediaJob(userId, uploadId, clock.instant()));
    }
}
