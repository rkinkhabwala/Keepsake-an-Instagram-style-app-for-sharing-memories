package com.keepsake.media.service;

import com.keepsake.media.config.MediaProperties;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** Decodes uploads to upright RGB pixels, guarding against decompression bombs. */
@Component
public class ImageDecoder {

    /** Fits a 48 MP phone photo with headroom; larger images are rejected before their pixels are decoded. */
    static final long MAX_PIXELS = 60_000_000L;
    /** Decode at no less than twice the display size, then let the resizer do a quality downscale. */
    private static final int SUBSAMPLE_TARGET_EDGE = ImageRenditions.DISPLAY_LONG_EDGE * 2;
    private static final long HEIF_TIMEOUT_SECONDS = 60;

    private final String heifDecoder;

    public ImageDecoder(MediaProperties properties) {
        this.heifDecoder = properties.heifDecoder();
    }

    /**
     * @param orientation EXIF orientation to apply. Ignored for HEIC, whose container transforms libheif
     *                    already applies.
     */
    public BufferedImage decode(byte[] bytes, ImageType type, int orientation) throws MediaProcessingException {
        if (type == ImageType.HEIC) {
            return decodeRaster(convertHeif(bytes), ImageType.PNG);
        }
        return orient(decodeRaster(bytes, type), orientation);
    }

    private BufferedImage decodeRaster(byte[] bytes, ImageType type) throws MediaProcessingException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = readerFor(type);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new MediaProcessingException("Image is too large (over 60 megapixels)");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int subsample = Math.max(1, Math.max(width, height) / SUBSAMPLE_TARGET_EDGE);
                param.setSourceSubsampling(subsample, subsample, 0, 0);
                return toRgb(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new MediaProcessingException("Image could not be decoded", exception);
        }
    }

    private static ImageReader readerFor(ImageType type) throws MediaProcessingException {
        if (type == ImageType.WEBP) {
            return new WebPImageReaderSpi().createReaderInstance(null);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.name().toLowerCase());
        if (!readers.hasNext()) {
            throw new MediaProcessingException("No decoder available for " + type);
        }
        return readers.next();
    }

    private byte[] convertHeif(byte[] bytes) throws MediaProcessingException {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("keepsake-heif-");
            Path input = Files.write(directory.resolve("input.heic"), bytes);
            Path output = directory.resolve("output.png");
            Process process = new ProcessBuilder(heifDecoder, input.toString(), output.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(HEIF_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaProcessingException("HEIC conversion timed out");
            }
            if (process.exitValue() != 0 || !Files.exists(output)) {
                throw new MediaProcessingException("HEIC image could not be decoded");
            }
            return Files.readAllBytes(output);
        } catch (IOException exception) {
            throw new MediaProcessingException("HEIC image could not be decoded", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("HEIC conversion was interrupted", exception);
        } finally {
            deleteQuietly(directory);
        }
    }

    /** Copies onto a plain RGB or ARGB raster so later steps see a predictable pixel layout. */
    private static BufferedImage toRgb(BufferedImage source) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getType() == type) {
            return source;
        }
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return target;
    }

    /** Applies an EXIF orientation (1–8) to the pixels, since the EXIF tag itself is stripped. */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height, source.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> { targetX = width - 1 - x; targetY = y; }
                    case 3 -> { targetX = width - 1 - x; targetY = height - 1 - y; }
                    case 4 -> { targetX = x; targetY = height - 1 - y; }
                    case 5 -> { targetX = y; targetY = x; }
                    case 6 -> { targetX = height - 1 - y; targetY = x; }
                    case 7 -> { targetX = height - 1 - y; targetY = width - 1 - x; }
                    default -> { targetX = y; targetY = width - 1 - x; }
                }
                target.setRGB(targetX, targetY, source.getRGB(x, y));
            }
        }
        return target;
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // Temp files are best-effort cleanup.
        }
    }
}
