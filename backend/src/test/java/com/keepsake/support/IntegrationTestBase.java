package com.keepsake.support;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one Postgres, one Redis, one MinIO, and one Kafka container for the whole test run so integration test classes share a
 * Spring context. Tests must therefore use unique usernames, emails, and client IPs. Every test class must use
 * this same configuration: a second context would run a second set of Kafka consumers in the same groups, so
 * jobs (and their metrics) could be handled by the other context. Metrics export is on, as in the running app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
public abstract class IntegrationTestBase {

    /** The address tests use to act as the web container's nginx. */
    protected static final String TRUSTED_PROXY = "192.0.2.250";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    /** Upstream MinIO no longer publishes free images; this community rebuild matches docker-compose.yml. */
    protected static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"));

    /** Same broker image and KRaft mode as docker-compose.yml. */
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void configureServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("keepsake.s3.endpoint", MINIO::getS3URL);
        registry.add("keepsake.s3.public-endpoint", MINIO::getS3URL);
        registry.add("keepsake.s3.access-key", MINIO::getUserName);
        registry.add("keepsake.s3.secret-key", MINIO::getPassword);
        registry.add("keepsake.trusted-proxies", () -> TRUSTED_PROXY);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Tests that need counts flushed to Postgres call EngagementCounters.flush() themselves.
        registry.add("keepsake.counters.flush-interval", () -> "PT1H");
        // Small enough that feeds fill up and celebrities exist in tests; tests refresh celebrities themselves.
        registry.add("keepsake.feed.capacity", () -> "6");
        registry.add("keepsake.feed.celebrity-threshold", () -> "4");
        registry.add("keepsake.feed.celebrity-refresh", () -> "PT1H");
    }
}
