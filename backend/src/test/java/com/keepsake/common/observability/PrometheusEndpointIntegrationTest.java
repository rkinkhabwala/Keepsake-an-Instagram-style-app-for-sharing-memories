package com.keepsake.common.observability;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec §11: /actuator/prometheus serves the metrics the Grafana dashboard is built on. Spring Boot tests turn
 * metrics export off by default, so this test turns it back on, as it is in the running app.
 */
@AutoConfigureObservability(tracing = false)
class PrometheusEndpointIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void servesFeedLatencyHistogramAndMediaTimersWithoutCredentials() throws Exception {
        TestApi api = new TestApi(mockMvc, objectMapper);
        TestUser user = api.signup("obs_user");
        api.createReadyMemory(user, "PUBLIC");
        mockMvc.perform(as(user, get("/api/feed"))).andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics).containsPattern(
                "http_server_requests_seconds_bucket\\{[^}]*application=\"keepsake\"[^}]*uri=\"/api/feed\"");
        assertThat(metrics).containsPattern("keepsake_media_processing_seconds_bucket\\{[^}]*outcome=\"ready\"");
        assertThat(metrics).containsPattern("keepsake_media_queue_wait_seconds_count\\{[^}]*\\} [1-9]");
        assertThat(metrics).containsPattern("executor_queued_tasks\\{[^}]*name=\"taskExecutor\"");
    }

    @Test
    void opensOnlyThePrometheusEndpoint() throws Exception {
        TestUser user = new TestApi(mockMvc, objectMapper).signup("obs_other");

        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(user, get("/actuator/env"))).andExpect(status().isNotFound());
        mockMvc.perform(as(user, get("/actuator/metrics"))).andExpect(status().isNotFound());
    }
}
