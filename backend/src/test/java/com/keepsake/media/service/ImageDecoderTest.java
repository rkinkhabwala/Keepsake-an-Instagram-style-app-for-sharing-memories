package com.keepsake.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepsake.media.config.MediaProperties;
import com.keepsake.support.TestImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImageDecoderTest {

    @TempDir
    Path tempDir;

    /** A 3x2 image whose pixel values encode their (x, y) position. */
    private static BufferedImage labelled() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                image.setRGB(x, y, label(x, y));
            }
        }
        return image;
    }

    private static int label(int x, int y) {
        return 0xFF000000 | (x << 8) | y;
    }

    /** Where source pixel (0,0) lands for each EXIF orientation, and the resulting size. */
    @ParameterizedTest(name = "orientation {0}")
    @CsvSource({
            "1, 3, 2, 0, 0",
            "2, 3, 2, 2, 0",
            "3, 3, 2, 2, 1",
            "4, 3, 2, 0, 1",
            "5, 2, 3, 0, 0",
            "6, 2, 3, 1, 0",
            "7, 2, 3, 1, 2",
            "8, 2, 3, 0, 2"
    })
    void appliesExifOrientationToPixels(int orientation, int width, int height, int originX, int originY) {
        BufferedImage oriented = ImageDecoder.orient(labelled(), orientation);

        assertThat(oriented.getWidth()).isEqualTo(width);
        assertThat(oriented.getHeight()).isEqualTo(height);
        assertThat(oriented.getRGB(originX, originY)).isEqualTo(label(0, 0));
    }

    @Test
    void rotationSixTurnsTopRowIntoRightColumn() {
        BufferedImage oriented = ImageDecoder.orient(labelled(), 6);

        // Rotating 90° clockwise: the source's top row (y=0) becomes the rightmost column, read top to bottom.
        assertThat(oriented.getRGB(1, 0)).isEqualTo(label(0, 0));
        assertThat(oriented.getRGB(1, 1)).isEqualTo(label(1, 0));
        assertThat(oriented.getRGB(1, 2)).isEqualTo(label(2, 0));
    }

    @Test
    void decodesJpegAndAppliesOrientation() throws Exception {
        ImageDecoder decoder = new ImageDecoder(new MediaProperties("unused", "unused", null));

        BufferedImage image = decoder.decode(TestImages.jpeg(40, 20), ImageType.JPEG, 6);

        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(40);
    }

    @Test
    void rejectsImagesOverThePixelLimitBeforeDecodingPixels() {
        ImageDecoder decoder = new ImageDecoder(new MediaProperties("unused", "unused", null));

        assertThatThrownBy(() -> decoder.decode(pngHeaderOnly(10_000, 10_000), ImageType.PNG, 1))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rejectsCorruptImages() {
        ImageDecoder decoder = new ImageDecoder(new MediaProperties("unused", "unused", null));
        byte[] truncated = java.util.Arrays.copyOf(TestImages.jpeg(64, 64), 20);

        assertThatThrownBy(() -> decoder.decode(truncated, ImageType.JPEG, 1))
                .isInstanceOf(MediaProcessingException.class);
    }

    @Test
    void decodesHeicThroughConfiguredDecoderWithoutReapplyingOrientation() throws Exception {
        Path png = Files.write(tempDir.resolve("decoded.png"), TestImages.png(30, 10));
        Path script = executable("heif-dec-stub", "#!/bin/sh\ncp '" + png + "' \"$2\"\n");
        ImageDecoder decoder = new ImageDecoder(new MediaProperties(script.toString(), heifInfo(30, 10).toString(), null));

        BufferedImage image = decoder.decode(new byte[] {1, 2, 3}, ImageType.HEIC, 6);

        assertThat(image.getWidth()).isEqualTo(30);
        assertThat(image.getHeight()).isEqualTo(10);
    }

    @Test
    void reportsHeicDecoderFailure() throws Exception {
        Path script = executable("heif-dec-failing", "#!/bin/sh\nexit 1\n");
        ImageDecoder decoder = new ImageDecoder(new MediaProperties(script.toString(), heifInfo(30, 10).toString(), null));

        assertThatThrownBy(() -> decoder.decode(new byte[] {1, 2, 3}, ImageType.HEIC, 1))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessage("HEIC image could not be decoded");
    }

    @Test
    void rejectsOversizedHeicFromItsHeaderWithoutDecodingIt() throws Exception {
        Path decoded = tempDir.resolve("decoder-ran");
        Path decoder = executable("heif-dec-spy", "#!/bin/sh\ntouch '" + decoded + "'\n");
        ImageDecoder imageDecoder = new ImageDecoder(
                new MediaProperties(decoder.toString(), heifInfo(30_000, 30_000).toString(), null));

        assertThatThrownBy(() -> imageDecoder.decode(new byte[] {1, 2, 3}, ImageType.HEIC, 1))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("too large");
        assertThat(decoded).doesNotExist();
    }

    @Test
    void readsTheLargestImageSizeFromHeifInfoOutput() throws Exception {
        String output = """
                MIME type: image/heic
                image: 4032x3024 (id=49), primary
                  thumbnail: 320x240
                image: 512x512 (id=1)
                """;

        assertThat(ImageDecoder.largestArea(output)).isEqualTo(4032L * 3024);
        assertThatThrownBy(() -> ImageDecoder.largestArea("MIME type: image/heic"))
                .isInstanceOf(MediaProcessingException.class);
    }

    /** A stand-in for libheif's heif-info that reports one primary image of the given size. */
    private Path heifInfo(int width, int height) throws Exception {
        return executable("heif-info-" + width + "x" + height,
                "#!/bin/sh\necho 'MIME type: image/heic'\necho 'image: " + width + "x" + height + " (id=1), primary'\n");
    }

    private Path executable(String name, String content) throws Exception {
        Path script = Files.writeString(tempDir.resolve(name), content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    /** A PNG whose header declares a huge size; the pixel data is never needed to reject it. */
    private static byte[] pngHeaderOnly(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        writeChunk(out, "IHDR", header.array());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
