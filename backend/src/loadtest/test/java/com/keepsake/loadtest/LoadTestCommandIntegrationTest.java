package com.keepsake.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.auth.service.JwtService;
import com.keepsake.feed.service.CelebrityAccounts;
import com.keepsake.support.IntegrationTestBase;
import java.io.File;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Runs only in a {@code mvn -Ploadtest} build: the command seeds, mints working tokens, and builds feeds. */
@ActiveProfiles("loadtest")
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false",
        "keepsake.loadtest.users=20",
        "keepsake.loadtest.celebrities=1",
        "keepsake.loadtest.follows-per-user=3",
        "keepsake.loadtest.memories-per-user=2",
        "keepsake.loadtest.memories-per-celebrity=3",
        "keepsake.loadtest.tokens=5",
        "keepsake.loadtest.tokens-file=target/loadtest/tokens.json",
        "keepsake.loadtest.exit-when-done=false"
})
class LoadTestCommandIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired CelebrityAccounts celebrities;

    @Test
    void seedsMintsWorkingTokensAndBuildsFeeds() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username LIKE 'lt\\_%'", Integer.class))
                .isEqualTo(21);
        UUID celebrity = jdbc.queryForObject("SELECT id FROM users WHERE username = 'lt_celeb_0'", UUID.class);
        assertThat(celebrities.contains(celebrity)).isTrue();

        JsonNode tokens = objectMapper.readTree(new File("target/loadtest/tokens.json"));
        assertThat(tokens).hasSize(5);
        for (JsonNode token : tokens) {
            UUID userId = jwtService.verifyAccessToken(token.asText());
            assertThat(redis.hasKey("feed:" + userId)).isTrue();
            JsonNode feed = objectMapper.readTree(mockMvc.perform(get("/api/feed")
                            .header("Authorization", "Bearer " + token.asText()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(feed.get("items")).isNotEmpty();
        }
    }
}
