package com.keepsake.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one Postgres, one Redis, and one MinIO container for the whole test run so integration test classes share a
 * Spring context. Tests must therefore use unique usernames, emails, and client IPs.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    /** The address tests use to act as the web container's nginx. */
    protected static final String TRUSTED_PROXY = "192.0.2.250";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    /** Upstream MinIO no longer publishes free images; this community rebuild matches docker-compose.yml. */
    protected static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"));

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
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
    }
}
