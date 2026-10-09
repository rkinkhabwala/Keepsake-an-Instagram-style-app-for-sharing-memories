package com.keepsake.media.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Runs processing off the request thread; the request id is carried over by the executor's MDC decorator. */
@Service
public class MediaProcessingService {

    private final MediaProcessor processor;
    private final Timer queueWait;
    private final Clock clock;

    public MediaProcessingService(MediaProcessor processor, MeterRegistry meterRegistry, Clock clock) {
        this.processor = processor;
        this.clock = clock;
        this.queueWait = Timer.builder("keepsake.media.queue.wait")
                .description("Time uploads wait between being submitted for processing and processing starting")
                .register(meterRegistry);
    }

    /**
     * Processes every upload even if an earlier one fails, so each records its own outcome.
     *
     * @param submittedAt when the work was handed to the queue, to measure queue wait (lag)
     * @return completes with true only when all uploads are READY
     */
    @Async
    public CompletableFuture<Boolean> processAll(List<UUID> uploadIds, Instant submittedAt) {
        queueWait.record(Duration.between(submittedAt, clock.instant()));
        boolean allReady = true;
        for (UUID uploadId : uploadIds) {
            allReady &= processor.process(uploadId);
        }
        return CompletableFuture.completedFuture(allReady);
    }
}
