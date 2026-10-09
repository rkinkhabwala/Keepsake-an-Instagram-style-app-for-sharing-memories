package com.keepsake.resurface;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class OnThisDayIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void returnsOwnAndTaggedMemoriesFromEarlierYearsGroupedNewestFirst() throws Exception {
        TestUser me = api.signup("otd_me");
        TestUser friend = api.signup("otd_friend");
        TestUser blocked = api.signup("otd_blocked");
        UUID mine2019 = memory(me, "2019-07-04", "ONLY_ME", List.of());
        UUID mine2022 = memory(me, "2022-07-04", "PUBLIC", List.of());
        UUID friendTagsMe2021 = memory(friend, "2021-07-04", "PUBLIC", List.of(me.username()));
        memory(friend, "2020-07-04", "ONLY_ME", List.of(me.username()));
        memory(friend, "2018-07-04", "PUBLIC", List.of());
        memory(me, "2022-07-05", "PUBLIC", List.of());
        memory(me, "2026-07-04", "PUBLIC", List.of());
        UUID deleted = memory(me, "2017-07-04", "PUBLIC", List.of());
        mockMvc.perform(as(me, delete("/api/memories/" + deleted))).andExpect(status().isNoContent());
        memory(blocked, "2016-07-04", "PUBLIC", List.of(me.username()));
        api.block(me, blocked);

        Map<Integer, List<String>> years = years(me, "2026-07-04");

        assertThat(years).containsExactly(
                Map.entry(2022, List.of(mine2022.toString())),
                Map.entry(2021, List.of(friendTagsMe2021.toString())),
                Map.entry(2019, List.of(mine2019.toString())));
        JsonNode body = onThisDay(me, "2026-07-04");
        assertThat(body.get("date").asText()).isEqualTo("2026-07-04");
        assertThat(body.at("/years/0/yearsAgo").asInt()).isEqualTo(4);
        assertThat(body.at("/years/0/memories/0/thumbUrl").asText()).contains("thumb/");
    }

    @Test
    void leapDayMemoriesShowOnFeb28OfNonLeapYearsAndOnFeb29OfLeapYears() throws Exception {
        TestUser me = api.signup("otd_leap");
        UUID leapDay = memory(me, "2020-02-29", "PUBLIC", List.of());
        UUID feb28 = memory(me, "2019-02-28", "PUBLIC", List.of());

        assertThat(years(me, "2021-02-28").values()).containsExactly(List.of(leapDay.toString()), List.of(feb28.toString()));
        assertThat(years(me, "2023-02-28").values()).containsExactly(List.of(leapDay.toString()), List.of(feb28.toString()));
        assertThat(years(me, "2024-02-28").values()).containsExactly(List.of(feb28.toString()));
        assertThat(years(me, "2024-02-29").values()).containsExactly(List.of(leapDay.toString()));
        assertThat(years(me, "2021-03-01")).isEmpty();
    }

    @Test
    void validatesTheDate() throws Exception {
        TestUser me = api.signup("otd_date");

        mockMvc.perform(as(me, get("/api/resurface/on-this-day").param("date", "July 4")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(me, get("/api/resurface/on-this-day"))).andExpect(status().isOk());
    }

    private UUID memory(TestUser author, String occurredOn, String visibility, List<String> tagged) throws Exception {
        UUID uploadId = api.upload(author, "image/jpeg", TestImages.jpeg(16, 16));
        UUID memoryId = api.createMemory(author, List.of(uploadId),
                Map.of("occurredOn", occurredOn, "visibility", visibility, "taggedUsernames", tagged));
        api.awaitStatus(author, memoryId, "READY");
        return memoryId;
    }

    private JsonNode onThisDay(TestUser viewer, String date) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(viewer, get("/api/resurface/on-this-day").param("date", date)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Map<Integer, List<String>> years(TestUser viewer, String date) throws Exception {
        Map<Integer, List<String>> years = new LinkedHashMap<>();
        onThisDay(viewer, date).get("years").forEach(year -> {
            List<String> ids = new ArrayList<>();
            year.get("memories").forEach(memory -> ids.add(memory.get("id").asText()));
            years.put(year.get("year").asInt(), ids);
        });
        return years;
    }
}
