package com.keepsake.media.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Processes a job's uploads, recording how long the job waited in the queue (outbox plus Kafka). */
@Service
public class MediaProcessingService {

    private final MediaProcessor processor;
    private final Timer queueWait;
    private final Clock clock;

    public MediaProcessingService(MediaProcessor processor, MeterRegistry meterRegistry, Clock clock) {
        this.processor = processor;
        this.clock = clock;
        this.queueWait = Timer.builder("keepsake.media.queue.wait")
                .description("Time between a media job being queued and its processing starting")
                .register(meterRegistry);
    }

    /**
     * Processes every upload even if an earlier one fails, so each records its own outcome. Uploads already READY
     * are skipped, so a redelivered job is harmless.
     *
     * @param submittedAt when the job was queued, to measure queue wait (lag)
     * @return true only when all uploads are READY
     */
    public boolean processAll(List<UUID> uploadIds, Instant submittedAt) {
        queueWait.record(Duration.between(submittedAt, clock.instant()));
        boolean allReady = true;
        for (UUID uploadId : uploadIds) {
            allReady &= processor.process(uploadId);
        }
        return allReady;
    }
}
