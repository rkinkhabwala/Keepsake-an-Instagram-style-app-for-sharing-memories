package com.keepsake.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.entity.MediaUploadStatus;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.ImageType;
import com.keepsake.media.service.MediaProcessingService;
import com.keepsake.media.service.MediaStorage;
import com.keepsake.media.service.MediaUploadService;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.support.TestImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class MediaPipelineIntegrationTest extends IntegrationTestBase {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MediaUploadRepository uploadRepository;

    @Autowired
    MediaUploadService uploadService;

    @Autowired
    MediaProcessingService processingService;

    @Autowired
    MediaStorage storage;

    @Test
    void processesJpegIntoExifFreeWebpRenditionsWithBlurhash() throws Exception {
        TestUser owner = signup("mp_full");
        // 1600x1200 landscape pixels with orientation 6 (rotate 90° clockwise) -> upright 1200x1600 portrait.
        byte[] original = TestImages.jpegWithExif(1600, 1200, 6, "2019:07:04 18:30:00", 40.7128, -74.0060);
        JsonNode slot = requestUploadUrls(owner, "image/jpeg", original.length).get(0);
        UUID uploadId = UUID.fromString(slot.get("uploadId").asText());

        assertThat(put(slot, original)).isEqualTo(200);
        mockMvc.perform(as(owner, get("/api/media/uploads/" + uploadId + "/metadata")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.takenOn").value("2019-07-04"))
                .andExpect(jsonPath("$.latitude").value(org.hamcrest.Matchers.closeTo(40.7128, 1e-4)))
                .andExpect(jsonPath("$.longitude").value(org.hamcrest.Matchers.closeTo(-74.0060, 1e-4)));

        uploadService.claimForProcessing(owner.id(), List.of(uploadId));
        assertThat(processingService.processAll(List.of(uploadId), java.time.Instant.now())).isTrue();

        MediaUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.READY);
        assertThat(upload.getWidth()).isEqualTo(810);
        assertThat(upload.getHeight()).isEqualTo(1080);
        assertThat(upload.getBlurhash()).hasSize(28);
        assertThat(upload.getTakenOn()).isEqualTo(LocalDate.of(2019, 7, 4));
        assertThat(upload.getGpsLat()).isCloseTo(40.7128, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(upload.getGpsLng()).isCloseTo(-74.0060, org.assertj.core.data.Offset.offset(1e-4));

        byte[] display = storage.read(upload.getDisplayKey());
        byte[] thumb = storage.read(upload.getThumbKey());
        assertThat(ImageType.detect(display)).contains(ImageType.WEBP);
        assertThat(ImageType.detect(thumb)).contains(ImageType.WEBP);
        assertThat(dimensions(thumb)).containsExactly(320, 320);
        assertThat(dimensions(display)).containsExactly(810, 1080);
        assertNoExif(display);
        assertNoExif(thumb);

        // Derived images are served through short-lived presigned GETs.
        HttpResponse<byte[]> served = HTTP.send(
                HttpRequest.newBuilder(storage.presignDownload(upload.getThumbKey()).toURI()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(thumb);
    }

    @Test
    void enlargesSmallImagesOnlyForTheSquareThumb() throws Exception {
        TestUser owner = signup("mp_small");
        UUID uploadId = uploadAndClaim(owner, "image/png", TestImages.png(200, 100));

        assertThat(processingService.processAll(List.of(uploadId), java.time.Instant.now())).isTrue();

        MediaUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        assertThat(upload.getWidth()).isEqualTo(200);
        assertThat(upload.getHeight()).isEqualTo(100);
        assertThat(upload.getTakenOn()).isNull();
        assertThat(upload.getGpsLat()).isNull();
        assertThat(dimensions(storage.read(upload.getThumbKey()))).containsExactly(320, 320);
    }

    @Test
    void failsWhenBytesDoNotMatchDeclaredType() throws Exception {
        TestUser owner = signup("mp_mismatch");
        UUID uploadId = uploadAndClaim(owner, "image/png", TestImages.jpeg(64, 64));

        assertThat(processingService.processAll(List.of(uploadId), java.time.Instant.now())).isFalse();

        MediaUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("File content does not match its declared type");
        assertThat(storage.size(upload.displayKeyFor())).isEmpty();
    }

    @Test
    void failsWhenFileIsNotAnImageAtAll() throws Exception {
        TestUser owner = signup("mp_notimage");
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        UUID uploadId = uploadAndClaim(owner, "image/jpeg", html);

        assertThat(processingService.processAll(List.of(uploadId), java.time.Instant.now())).isFalse();
        assertThat(uploadRepository.findById(uploadId).orElseThrow().getFailureReason())
                .isEqualTo("File is not a supported image");
    }

    @Test
    void batchReportsFailureWhenAnyUploadIsMissingButProcessesTheRest() throws Exception {
        TestUser owner = signup("mp_batch");
        UUID good = uploadAndClaim(owner, "image/jpeg", TestImages.jpeg(64, 64));
        JsonNode slot = requestUploadUrls(owner, "image/jpeg", 1000).get(0);
        UUID neverUploaded = UUID.fromString(slot.get("uploadId").asText());
        uploadService.claimForProcessing(owner.id(), List.of(neverUploaded));

        assertThat(processingService.processAll(List.of(neverUploaded, good), java.time.Instant.now())).isFalse();

        assertThat(uploadRepository.findById(good).orElseThrow().getStatus()).isEqualTo(MediaUploadStatus.READY);
        MediaUpload missing = uploadRepository.findById(neverUploaded).orElseThrow();
        assertThat(missing.getStatus()).isEqualTo(MediaUploadStatus.FAILED);
        assertThat(missing.getFailureReason()).isEqualTo("Uploaded file was not found");
    }

    @Test
    void presignedUploadRejectsBodyOfDifferentSizeOrType() throws Exception {
        TestUser owner = signup("mp_presign");
        byte[] image = TestImages.jpeg(64, 64);
        JsonNode slot = requestUploadUrls(owner, "image/jpeg", image.length).get(0);

        byte[] bigger = java.util.Arrays.copyOf(image, image.length + 100);
        assertThat(put(slot, bigger)).isNotEqualTo(200);
        HttpResponse<String> wrongType = HTTP.send(HttpRequest.newBuilder(URI.create(slot.get("url").asText()))
                        .header("Content-Type", "text/html")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(image))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(wrongType.statusCode()).isNotEqualTo(200);
        assertThat(put(slot, image)).isEqualTo(200);
    }

    @Test
    void uploadUrlRequestsAreValidated() throws Exception {
        TestUser owner = signup("mp_validate");

        expectBadRequest(owner, List.of(Map.of("contentType", "image/gif", "sizeBytes", 10)), "Unsupported");
        expectBadRequest(owner, List.of(Map.of("contentType", "video/mp4", "sizeBytes", 100L * 1024 * 1024 + 1)),
                "100 MB");
        expectBadRequest(owner, List.of(Map.of("contentType", "image/jpeg", "sizeBytes", 20 * 1024 * 1024 + 1)),
                "20 MB");
        expectBadRequest(owner, List.of(Map.of("contentType", "image/jpeg", "sizeBytes", 0)), "20 MB");
        expectBadRequest(owner, List.of(), "between 1 and 10");
        expectBadRequest(owner, java.util.Collections.nCopies(11, Map.of("contentType", "image/png", "sizeBytes", 5)),
                "between 1 and 10");

        mockMvc.perform(post("/api/media/upload-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"contentType\":\"image/png\",\"sizeBytes\":5}]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsCanOnlyBeClaimedOnceByTheirOwner() throws Exception {
        TestUser owner = signup("mp_claim_owner");
        TestUser other = signup("mp_claim_other");
        UUID uploadId = UUID.fromString(requestUploadUrls(owner, "image/png", 10).get(0).get("uploadId").asText());

        assertThatThrownBy(() -> uploadService.claimForProcessing(other.id(), List.of(uploadId)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> uploadService.claimForProcessing(owner.id(), List.of(uploadId, uploadId)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> uploadService.claimForProcessing(owner.id(), List.of(UUID.randomUUID())))
                .isInstanceOf(InvalidRequestException.class);

        uploadService.claimForProcessing(owner.id(), List.of(uploadId));
        assertThat(uploadRepository.findById(uploadId).orElseThrow().getStatus())
                .isEqualTo(MediaUploadStatus.PROCESSING);
        assertThatThrownBy(() -> uploadService.claimForProcessing(owner.id(), List.of(uploadId)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void uploadMetadataIsOnlyVisibleToTheUploader() throws Exception {
        TestUser owner = signup("mp_meta_owner");
        TestUser other = signup("mp_meta_other");
        byte[] image = TestImages.jpeg(32, 32);
        JsonNode slot = requestUploadUrls(owner, "image/jpeg", image.length).get(0);
        String path = "/api/media/uploads/" + slot.get("uploadId").asText() + "/metadata";

        mockMvc.perform(as(owner, get(path))).andExpect(status().isConflict());
        put(slot, image);

        mockMvc.perform(as(owner, get(path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.takenOn").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist());
        mockMvc.perform(as(other, get(path))).andExpect(status().isNotFound());
    }

    private record TestUser(UUID id, String accessToken) {
    }

    private UUID uploadAndClaim(TestUser owner, String contentType, byte[] bytes) throws Exception {
        JsonNode slot = requestUploadUrls(owner, contentType, bytes.length).get(0);
        assertThat(put(slot, bytes)).isEqualTo(200);
        UUID uploadId = UUID.fromString(slot.get("uploadId").asText());
        uploadService.claimForProcessing(owner.id(), List.of(uploadId));
        return uploadId;
    }

    private JsonNode requestUploadUrls(TestUser owner, String contentType, long sizeBytes) throws Exception {
        String body = mockMvc.perform(as(owner, post("/api/media/upload-urls"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                List.of(Map.of("contentType", contentType, "sizeBytes", sizeBytes)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].method").value("PUT"))
                .andExpect(jsonPath("$[0].headers['Content-Type']").value(contentType))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void expectBadRequest(TestUser owner, List<?> items, String detail) throws Exception {
        mockMvc.perform(as(owner, post("/api/media/upload-urls"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(detail)));
    }

    private static int put(JsonNode slot, byte[] bytes) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(slot.get("url").asText()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        slot.get("headers").fields().forEachRemaining(header ->
                request.header(header.getKey(), header.getValue().asText()));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static int[] dimensions(byte[] webp) throws Exception {
        var reader = new com.luciad.imageio.webp.WebPImageReaderSpi().createReaderInstance(null);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(webp))) {
            reader.setInput(input);
            BufferedImage image = reader.read(0);
            return new int[] {image.getWidth(), image.getHeight()};
        } finally {
            reader.dispose();
        }
    }

    private static void assertNoExif(byte[] image) throws Exception {
        Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(image), image.length);
        assertThat(metadata.getDirectoriesOfType(ExifIFD0Directory.class)).isEmpty();
        assertThat(metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)).isEmpty();
        assertThat(metadata.getDirectoriesOfType(GpsDirectory.class)).isEmpty();
    }

    private TestUser signup(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup")
                        .with(request -> {
                            request.setRemoteAddr("192.0.3." + NEXT_IP.getAndIncrement());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.test",
                                "password", "Keepsake-password-2026",
                                "displayName", username))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new TestUser(UUID.fromString(json.at("/user/id").asText()), json.get("accessToken").asText());
    }

    private static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + user.accessToken());
    }
}
