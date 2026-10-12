package com.keepsake.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.events.Topics;
import com.keepsake.media.service.MediaJobs.AvatarMediaJob;
import com.keepsake.media.service.MediaProcessingService;
import java.util.List;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Processes avatar uploads from Kafka; the avatar changes only if this is still the latest one requested. */
@Component
public class AvatarJobListener {

    private final MediaProcessingService processingService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AvatarJobListener(MediaProcessingService processingService, UserService userService, ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.AVATAR_MEDIA, groupId = "keepsake-avatar-media")
    public void onJob(String payload) throws JsonProcessingException {
        AvatarMediaJob job = objectMapper.readValue(payload, AvatarMediaJob.class);
        if (processingService.processAll(List.of(job.uploadId()), job.submittedAt())) {
            userService.applyAvatar(job.userId(), job.uploadId());
        }
    }
}
