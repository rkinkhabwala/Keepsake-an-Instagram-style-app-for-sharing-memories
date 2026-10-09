package com.keepsake.media;

import static com.keepsake.support.TestApi.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestApi;
import com.keepsake.support.TestApi.TestUser;
import com.keepsake.support.TestImages;
import com.keepsake.support.TestVideos;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Video support (spec §6, Phase 3): poster + 720p H.264 MP4, metadata stripped, limits enforced. */
class VideoIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MediaUploadRepository uploadRepository;

    @TempDir Path temp;

    TestApi api;

    @BeforeAll
    static void requireFfmpeg() {
        TestVideos.assumeFfmpeg();
    }

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void videoBecomesAMetadataFree720pMp4WithPosterThumbAndBlurhash() throws Exception {
        TestUser author = api.signup("vid_basic");
        byte[] source = TestVideos.generate(temp, 640, 360, 2, 24, "mp4");
        UUID uploadId = api.upload(author, "video/mp4", source);

        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("visibility", "PUBLIC"));
        JsonNode memory = api.awaitStatus(author, memoryId, "READY");

        JsonNode media = memory.at("/media/0");
        assertThat(media.get("type").asText()).isEqualTo("VIDEO");
        assertThat(media.get("width").asInt()).isEqualTo(640);
        assertThat(media.get("height").asInt()).isEqualTo(360);
        assertThat(media.get("durationMs").asInt()).isBetween(1800, 2200);
        assertThat(media.get("displayUrl").asText()).contains("video/" + uploadId + ".mp4");
        assertThat(media.get("posterUrl").asText()).contains("display/" + uploadId + ".webp");
        assertThat(media.get("thumbUrl").asText()).contains("thumb/" + uploadId + ".webp");
        assertThat(media.get("blurhash").asText()).hasSize(28);

        // The served MP4 is H.264 + AAC and carries none of the original's metadata.
        Path served = download(media.get("displayUrl").asText(), "served.mp4");
        String probe = TestVideos.probe(served);
        assertThat(probe).contains("\"codec_name\": \"h264\"").contains("\"codec_name\": \"aac\"");
        assertThat(probe).doesNotContain("location").doesNotContain("creation_time").doesNotContain("40.7128");
        assertThat(TestVideos.probe(temp.resolve("source-640x360-2.mp4"))).contains("location");

        mockMvc.perform(as(author, get("/api/users/vid_basic/memories")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].video")
                        .value(true));
    }

    @Test
    void tallVideoIsScaledToAShortSideOf720AndMovIsAccepted() throws Exception {
        TestUser author = api.signup("vid_tall");
        UUID tall = api.upload(author, "video/quicktime", TestVideos.generate(temp, 1080, 1920, 1, 10, "mov"));
        UUID photo = api.upload(author, "image/jpeg", TestImages.jpeg(64, 48));

        UUID memoryId = api.createMemory(author, List.of(tall, photo), Map.of("visibility", "PUBLIC"));
        JsonNode memory = api.awaitStatus(author, memoryId, "READY");

        assertThat(memory.at("/media/0/type").asText()).isEqualTo("VIDEO");
        assertThat(memory.at("/media/0/width").asInt()).isEqualTo(720);
        assertThat(memory.at("/media/0/height").asInt()).isEqualTo(1280);
        assertThat(memory.at("/media/1/type").asText()).isEqualTo("IMAGE");
        assertThat(memory.at("/media/1/posterUrl").isNull()).isTrue();
    }

    @Test
    void videosLongerThanSixtySecondsFail() throws Exception {
        TestUser author = api.signup("vid_long");
        UUID uploadId = api.upload(author, "video/mp4", TestVideos.generate(temp, 64, 64, 62, 1, "mp4"));

        UUID memoryId = api.createMemory(author, List.of(uploadId), Map.of("visibility", "PUBLIC"));
        api.awaitStatus(author, memoryId, "FAILED");

        assertThat(failureOf(uploadId)).isEqualTo("Videos can be at most 60 seconds");
    }

    @Test
    void filesThatAreNotVideoFailWhateverTheirDeclaredType() throws Exception {
        TestUser author = api.signup("vid_fake");
        UUID image = api.upload(author, "video/mp4", TestImages.jpeg(32, 32));
        UUID text = api.upload(author, "video/quicktime", "#EXTM3U\nhttp://169.254.169.254/latest\n".getBytes());

        api.awaitStatus(author, api.createMemory(author, List.of(image), Map.of()), "FAILED");
        api.awaitStatus(author, api.createMemory(author, List.of(text), Map.of()), "FAILED");

        assertThat(failureOf(image)).isEqualTo("File content does not match its declared type");
        assertThat(failureOf(text)).isEqualTo("File is not a supported video");
    }

    private String failureOf(UUID uploadId) {
        MediaUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        return upload.getFailureReason();
    }

    private Path download(String url, String name) throws Exception {
        HttpResponse<Path> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(temp.resolve(name)));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Files.size(response.body())).isPositive();
        return response.body();
    }
}
