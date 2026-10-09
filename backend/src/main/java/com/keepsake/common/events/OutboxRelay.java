package com.keepsake.common.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes committed outbox events to Kafka in creation order and marks them published. Rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED}, so several app instances can relay at once without sending an event twice
 * (Kafka delivery is still at-least-once if the app stops after sending but before marking; consumers are
 * idempotent). A failed send leaves the row pending, with its attempt count and error, for the next run; later
 * events with the same topic and key wait behind it (to keep their order), but other events still go out.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();

    public OutboxRelay(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            KafkaTemplate<String, String> kafka,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.clock = clock;
        Gauge.builder("keepsake.outbox.pending", pending, AtomicLong::get)
                .description("Outbox events committed but not yet published to Kafka")
                .register(meterRegistry);
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }

    @Scheduled(fixedDelayString = "${keepsake.outbox.relay-interval:PT0.2S}")
    public void relay() {
        int sent;
        do {
            sent = relayBatch();
        } while (sent == BATCH);
        Long remaining = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        pending.set(remaining == null ? 0 : remaining);
    }

    /** @return how many events were published */
    int relayBatch() {
        Integer published = transactions.execute(status -> {
            List<Row> rows = jdbc.query("""
                    SELECT id, topic, event_key, payload FROM outbox_events
                    WHERE published_at IS NULL
                    ORDER BY created_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """,
                    (rs, rowNum) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                            rs.getString("event_key"), rs.getString("payload")),
                    BATCH);
            int count = 0;
            java.util.Set<String> blocked = new java.util.HashSet<>();
            for (Row row : rows) {
                String stream = row.topic() + "|" + row.key();
                if (blocked.contains(stream)) {
                    continue;
                }
                try {
                    kafka.send(row.topic(), row.key(), row.payload()).get(10, TimeUnit.SECONDS);
                    jdbc.update("UPDATE outbox_events SET published_at = ?, attempts = attempts + 1 WHERE id = ?",
                            Timestamp.from(clock.instant()), row.id());
                    count++;
                } catch (Exception exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    String error = String.valueOf(exception.getMessage());
                    jdbc.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                            error.length() > 500 ? error.substring(0, 500) : error, row.id());
                    log.warn("Could not publish outbox event {} to {}; will retry", row.id(), row.topic(), exception);
                    blocked.add(stream);
                }
            }
            return count;
        });
        return published == null ? 0 : published;
    }
}
