package com.keepsake.memory.service;

import com.keepsake.media.config.MediaProperties;
import com.keepsake.media.service.MediaProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts media processing only once the memory and its claimed uploads are committed. */
@Component
public class MemoryProcessingListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryProcessingListener.class);

    private final MediaProcessingService processingService;
    private final MemoryService memoryService;
    private final MediaProperties mediaProperties;

    public MemoryProcessingListener(
            MediaProcessingService processingService,
            MemoryService memoryService,
            MediaProperties mediaProperties
    ) {
        this.processingService = processingService;
        this.memoryService = memoryService;
        this.mediaProperties = mediaProperties;
    }

    @TransactionalEventListener
    public void onSubmitted(MemoryMediaSubmitted event) {
        try {
            processingService.processAll(event.uploadIds()).whenComplete((allReady, error) -> {
                if (error != null) {
                    log.error("Processing memory {} failed unexpectedly", event.memoryId(), error);
                }
                memoryService.completeProcessing(event.memoryId());
            });
        } catch (TaskRejectedException busy) {
            // The processing queue is full: fail now so the author can retry, rather than waiting for the sweep.
            log.warn("Processing queue full; memory {} marked FAILED for retry", event.memoryId());
            memoryService.completeProcessing(event.memoryId());
        }
    }

    /** Safety net for processing lost to a restart or an error while recording the result. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void failStaleProcessing() {
        int failed = memoryService.failStaleProcessing(mediaProperties.processingTimeout());
        if (failed > 0) {
            log.warn("Marked {} memories stuck in PROCESSING as FAILED so they can be retried", failed);
        }
    }
}
