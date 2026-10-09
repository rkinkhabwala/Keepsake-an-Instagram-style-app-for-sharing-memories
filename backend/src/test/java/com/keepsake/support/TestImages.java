package com.keepsake.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Builds test images in memory, including JPEGs carrying a hand-written EXIF block. */
public final class TestImages {

    private TestImages() {
    }

    public static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 255 / width) << 16) | ((y * 255 / height) << 8) | 0x60);
            }
        }
        return image;
    }

    public static byte[] png(int width, int height) {
        return encode(gradient(width, height), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(gradient(width, height), "jpg");
    }

    /**
     * A JPEG with an EXIF APP1 segment holding Orientation, DateTimeOriginal ("yyyy:MM:dd HH:mm:ss"), and GPS.
     */
    public static byte[] jpegWithExif(
            int width,
            int height,
            int orientation,
            String dateTimeOriginal,
            double latitude,
            double longitude
    ) {
        byte[] jpeg = jpeg(width, height);
        byte[] tiff = exifTiff(orientation, dateTimeOriginal, latitude, longitude);
        ByteBuffer app1 = ByteBuffer.allocate(4 + 6 + tiff.length).order(ByteOrder.BIG_ENDIAN);
        app1.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (2 + 6 + tiff.length));
        app1.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII)).put(tiff);

        // Insert right after the SOI marker.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.writeBytes(app1.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] exifTiff(int orientation, String dateTimeOriginal, double latitude, double longitude) {
        ByteBuffer tiff = ByteBuffer.allocate(190).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        // IFD0 at 8: Orientation, Exif IFD pointer, GPS IFD pointer.
        tiff.putShort((short) 3);
        entry(tiff, 0x0112, 3, 1, orientation << 16);
        entry(tiff, 0x8769, 4, 1, 50);
        entry(tiff, 0x8825, 4, 1, 88);
        tiff.putInt(0);
        // Exif IFD at 50: DateTimeOriginal, whose 20-byte value sits at 68.
        tiff.putShort((short) 1);
        entry(tiff, 0x9003, 2, 20, 68);
        tiff.putInt(0);
        tiff.put((dateTimeOriginal + "\0").getBytes(StandardCharsets.US_ASCII));
        // GPS IFD at 88: refs inline, rationals at 142 and 166.
        tiff.putShort((short) 4);
        entry(tiff, 0x0001, 2, 2, (latitude >= 0 ? 'N' : 'S') << 24);
        entry(tiff, 0x0002, 5, 3, 142);
        entry(tiff, 0x0003, 2, 2, (longitude >= 0 ? 'E' : 'W') << 24);
        entry(tiff, 0x0004, 5, 3, 166);
        tiff.putInt(0);
        degreesMinutesSeconds(tiff, Math.abs(latitude));
        degreesMinutesSeconds(tiff, Math.abs(longitude));
        return tiff.array();
    }

    private static void entry(ByteBuffer buffer, int tag, int type, int count, int value) {
        buffer.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void degreesMinutesSeconds(ByteBuffer buffer, double value) {
        int degrees = (int) value;
        double remainingMinutes = (value - degrees) * 60;
        int minutes = (int) remainingMinutes;
        long secondsTimes10000 = Math.round((remainingMinutes - minutes) * 60 * 10000);
        buffer.putInt(degrees).putInt(1);
        buffer.putInt(minutes).putInt(1);
        buffer.putInt((int) secondsTimes10000).putInt(10000);
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
