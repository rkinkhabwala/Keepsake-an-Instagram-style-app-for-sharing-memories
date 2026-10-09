package com.keepsake.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.events.Topics;
import com.keepsake.notification.service.NotificationEvents.NotificationEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Turns social and memory events into notifications. Redelivery is safe: events are recorded at most once. */
@Component
public class NotificationListener {

    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public NotificationListener(NotificationService notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {Topics.SOCIAL_EVENTS, Topics.MEMORY_EVENTS}, groupId = "keepsake-notifications",
            concurrency = "${keepsake.notifications.consumers:3}")
    public void onEvent(String payload) throws JsonProcessingException {
        notifications.record(objectMapper.readValue(payload, NotificationEvent.class));
    }
}
