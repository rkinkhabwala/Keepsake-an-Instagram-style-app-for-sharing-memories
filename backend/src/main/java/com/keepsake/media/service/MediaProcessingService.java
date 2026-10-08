package com.keepsake.media.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Runs processing off the request thread; the request id is carried over by the executor's MDC decorator. */
@Service
public class MediaProcessingService {

    private final MediaProcessor processor;

    public MediaProcessingService(MediaProcessor processor) {
        this.processor = processor;
    }

    /**
     * Processes every upload even if an earlier one fails, so each records its own outcome.
     *
     * @return completes with true only when all uploads are READY
     */
    @Async
    public CompletableFuture<Boolean> processAll(List<UUID> uploadIds) {
        boolean allReady = true;
        for (UUID uploadId : uploadIds) {
            allReady &= processor.process(uploadId);
        }
        return CompletableFuture.completedFuture(allReady);
    }
}
