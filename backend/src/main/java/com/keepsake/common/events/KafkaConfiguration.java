package com.keepsake.common.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    static final int PARTITIONS = 3;

    @Bean
    NewTopic memoryMediaTopic() {
        return TopicBuilder.name(Topics.MEMORY_MEDIA).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic memoryMediaDeadLetterTopic() {
        return TopicBuilder.name(Topics.MEMORY_MEDIA + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic avatarMediaTopic() {
        return TopicBuilder.name(Topics.AVATAR_MEDIA).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic avatarMediaDeadLetterTopic() {
        return TopicBuilder.name(Topics.AVATAR_MEDIA + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * A record whose handling keeps failing is retried three times a second apart, then moved to
     * {@code <topic>.DLT} (same partition) so it cannot block the records behind it.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        // Named explicitly: this Spring Kafka version defaults to "<topic>-dlt", which would not match the topics
        // created above.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
