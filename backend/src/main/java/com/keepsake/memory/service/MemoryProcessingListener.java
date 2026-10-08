package com.keepsake.memory.service;

import com.keepsake.media.service.MediaProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts media processing only once the memory and its claimed uploads are committed. */
@Component
public class MemoryProcessingListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryProcessingListener.class);

    private final MediaProcessingService processingService;
    private final MemoryService memoryService;

    public MemoryProcessingListener(MediaProcessingService processingService, MemoryService memoryService) {
        this.processingService = processingService;
        this.memoryService = memoryService;
    }

    @TransactionalEventListener
    public void onSubmitted(MemoryMediaSubmitted event) {
        processingService.processAll(event.uploadIds()).whenComplete((allReady, error) -> {
            if (error != null) {
                log.error("Processing memory {} failed unexpectedly", event.memoryId(), error);
            }
            memoryService.completeProcessing(event.memoryId());
        });
    }
}
