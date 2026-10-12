package com.keepsake.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.events.Topics;
import com.keepsake.media.service.MediaJobs.MemoryMediaJob;
import com.keepsake.media.service.MediaProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Processes a memory's uploads from Kafka, then records the outcome (READY or FAILED). Redelivery is safe:
 * finished uploads are skipped and completing a memory is idempotent. A record that cannot be read goes to the
 * dead-letter topic after retries.
 */
@Component
public class MemoryMediaJobListener {

    private final MediaProcessingService processingService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    public MemoryMediaJobListener(
            MediaProcessingService processingService,
            MemoryService memoryService,
            ObjectMapper objectMapper
    ) {
        this.processingService = processingService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.MEMORY_MEDIA, groupId = "keepsake-memory-media",
            concurrency = "${keepsake.media.consumers:3}")
    public void onJob(String payload) throws JsonProcessingException {
        MemoryMediaJob job = objectMapper.readValue(payload, MemoryMediaJob.class);
        processingService.processAll(job.uploadIds(), job.submittedAt());
        memoryService.completeProcessing(job.memoryId());
    }
}
