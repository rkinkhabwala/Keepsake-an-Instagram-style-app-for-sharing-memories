package com.keepsake.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepsake.support.TestImages;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BlurhashTest {

    private static final String BASE83 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    @Test
    void solidColorEncodesComponentCountAndAverageColor() {
        BufferedImage red = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                red.setRGB(x, y, 0xFF0000);
            }
        }

        String hash = Blurhash.encode(red, 4, 3);

        // Size flag 'L' = (4-1) + (3-1)*9; characters 2-5 hold the DC term, the image's average sRGB colour.
        assertThat(hash).hasSize(28).startsWith("L");
        assertThat(decode83(hash.substring(2, 6))).isEqualTo(0xFF0000);
    }

    @Test
    void differentImagesProduceDifferentValidHashes() {
        String gradient = Blurhash.encode(TestImages.gradient(64, 48), 4, 3);
        String rotated = Blurhash.encode(TestImages.gradient(48, 64), 4, 3);

        assertThat(gradient).hasSize(28).matches("[0-9A-Za-z#$%*+,\\-.:;=?@\\[\\]^_{|}~]+");
        assertThat(gradient).isNotEqualTo(rotated);
    }

    @Test
    void rejectsOutOfRangeComponentCounts() {
        assertThatThrownBy(() -> Blurhash.encode(TestImages.gradient(4, 4), 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int decode83(String value) {
        int result = 0;
        for (char c : value.toCharArray()) {
            result = result * 83 + BASE83.indexOf(c);
        }
        return result;
    }
}
