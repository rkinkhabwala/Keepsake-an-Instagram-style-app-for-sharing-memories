package com.keepsake.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.support.TestImages;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsFormatsFromMagicBytes() throws Exception {
        assertThat(ImageType.detect(TestImages.jpeg(8, 8))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(TestImages.png(8, 8))).contains(ImageType.PNG);
        assertThat(ImageType.detect(new ImageRenditions().webp(TestImages.gradient(8, 8)))).contains(ImageType.WEBP);
        try (InputStream heic = getClass().getResourceAsStream("/media/sample.heic")) {
            assertThat(ImageType.detect(heic.readAllBytes())).contains(ImageType.HEIC);
        }
    }

    @Test
    void rejectsUnknownAndTruncatedContent() {
        assertThat(ImageType.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
    }

    @Test
    void mapsDeclaredContentTypes() {
        assertThat(ImageType.fromContentType("IMAGE/JPEG")).contains(ImageType.JPEG);
        assertThat(ImageType.fromContentType("image/heif")).contains(ImageType.HEIC);
        assertThat(ImageType.fromContentType("image/gif")).isEmpty();
        assertThat(ImageType.fromContentType(null)).isEmpty();
    }
}
