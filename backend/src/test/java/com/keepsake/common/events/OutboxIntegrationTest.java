package com.keepsake.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxIntegrationTest extends IntegrationTestBase {

    @Autowired Outbox outbox;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void committedEventsArePublishedInOrderAndMarked() throws Exception {
        String topic = "keepsake.test.outbox." + UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            outbox.publish(topic, "k1", Map.of("n", 1));
            outbox.publish(topic, "k1", Map.of("n", 2));
            outbox.publish(topic, "k2", Map.of("n", 3));
        });

        List<ConsumerRecord<String, String>> records = consume(topic, 3);

        assertThat(records).extracting(ConsumerRecord::key).containsExactlyInAnyOrder("k1", "k1", "k2");
        assertThat(records.stream().filter(record -> record.key().equals("k1")).map(ConsumerRecord::value))
                .containsExactly("{\"n\":1}", "{\"n\":2}");
        TestApi.await(Duration.ofSeconds(10), () -> pending(topic) == 0);
    }

    @Test
    void eventsFromARolledBackTransactionAreNeverPublished() throws Exception {
        String topic = "keepsake.test.rollback." + UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            outbox.publish(topic, "k", Map.of("n", 1));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE topic = ?", Long.class, topic)).isZero();
    }

    @Test
    void aFailingEventStaysPendingWithoutBlockingOthers() throws Exception {
        String badTopic = "not a valid topic name!";
        String goodTopic = "keepsake.test.after-failure." + UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            outbox.publish(badTopic, "k", Map.of("n", 1));
            outbox.publish(goodTopic, "k", Map.of("n", 2));
        });

        assertThat(consume(goodTopic, 1)).extracting(ConsumerRecord::value).containsExactly("{\"n\":2}");
        TestApi.await(Duration.ofSeconds(10), () -> jdbc.queryForObject(
                "SELECT attempts FROM outbox_events WHERE topic = ?", Integer.class, badTopic) > 0);
        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT published_at, last_error FROM outbox_events WHERE topic = ?", badTopic);
        assertThat(failed.get("published_at")).isNull();
        assertThat((String) failed.get("last_error")).isNotBlank();
        jdbc.update("DELETE FROM outbox_events WHERE topic = ?", badTopic);
    }

    @Test
    void anUnreadableMediaJobGoesToTheDeadLetterTopicAndProcessingContinues() throws Exception {
        String poison = "{not json " + UUID.randomUUID();
        kafka.send(Topics.MEMORY_MEDIA, "poison", poison).get();

        assertThat(consumeUntil(Topics.MEMORY_MEDIA + ".DLT", poison, Duration.ofSeconds(30))).isTrue();

        // The consumer moved on: a new memory still finishes processing through Kafka.
        TestApi api = new TestApi(mockMvc, objectMapper);
        TestUser user = api.signup("outbox_after_poison");
        api.createReadyMemory(user, "PUBLIC");
    }

    private long pending(String topic) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE topic = ? AND published_at IS NULL",
                Long.class, topic);
    }

    private static KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private static List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    private static boolean consumeUntil(String topic, String value, Duration timeout) {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (value.equals(record.value())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
