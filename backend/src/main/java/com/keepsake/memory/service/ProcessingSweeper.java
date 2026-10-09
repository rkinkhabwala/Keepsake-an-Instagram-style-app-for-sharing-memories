package com.keepsake.memory.service;

import com.keepsake.media.config.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net: memories PROCESSING for longer than the timeout are marked FAILED so their authors can retry.
 * Processing can be lost if a job lands in the dead-letter topic or recording its result fails.
 */
@Component
public class ProcessingSweeper {

    private static final Logger log = LoggerFactory.getLogger(ProcessingSweeper.class);

    private final MemoryService memoryService;
    private final MediaProperties mediaProperties;

    public ProcessingSweeper(MemoryService memoryService, MediaProperties mediaProperties) {
        this.memoryService = memoryService;
        this.mediaProperties = mediaProperties;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void failStaleProcessing() {
        int failed = memoryService.failStaleProcessing(mediaProperties.processingTimeout());
        if (failed > 0) {
            log.warn("Marked {} memories stuck in PROCESSING as FAILED so they can be retried", failed);
        }
    }
}
