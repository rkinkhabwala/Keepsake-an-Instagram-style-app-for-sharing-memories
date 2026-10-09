package com.keepsake.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.id.UuidV7;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an event to publish. Must run inside the transaction making the change it describes, so the event is
 * committed (or rolled back) together with that change; {@link OutboxRelay} sends it to Kafka afterwards.
 */
@Component
public class Outbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public Outbox(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event cannot be serialized: " + event, exception);
        }
        jdbc.update("INSERT INTO outbox_events (id, topic, event_key, payload, created_at) VALUES (?, ?, ?, ?, ?)",
                UuidV7.generate(), topic, key, payload, Timestamp.from(clock.instant()));
    }
}
