package com.keepsake.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.support.TestImages;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VideoTypeTest {

    private static byte[] header(String box, String brand) {
        byte[] bytes = new byte[16];
        System.arraycopy(box.getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 8, 4);
        return bytes;
    }

    @Test
    void detectsMp4AndQuickTimeContainers() {
        assertThat(VideoType.detect(header("ftyp", "isom"))).contains(VideoType.MP4);
        assertThat(VideoType.detect(header("ftyp", "mp42"))).contains(VideoType.MP4);
        assertThat(VideoType.detect(header("ftyp", "qt  "))).contains(VideoType.MOV);
        assertThat(VideoType.detect(header("moov", "xxxx"))).contains(VideoType.MOV);
    }

    @Test
    void rejectsImagesHeicAndOtherFiles() {
        assertThat(VideoType.detect(header("ftyp", "heic"))).isEmpty();
        assertThat(VideoType.detect(TestImages.jpeg(8, 8))).isEmpty();
        assertThat(VideoType.detect("<html></html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(VideoType.detect(new byte[3])).isEmpty();
    }

    @Test
    void mapsDeclaredContentTypes() {
        assertThat(VideoType.fromContentType("VIDEO/MP4")).contains(VideoType.MP4);
        assertThat(VideoType.fromContentType("video/quicktime")).contains(VideoType.MOV);
        assertThat(VideoType.fromContentType("video/webm")).isEmpty();
    }
}
