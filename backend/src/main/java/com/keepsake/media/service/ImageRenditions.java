package com.keepsake.media.service;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPImageWriterSpi;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Component;

/**
 * Derived images. They are encoded from decoded pixels only, so no EXIF or other metadata from the original
 * can carry over.
 */
@Component
public class ImageRenditions {

    public static final int DISPLAY_LONG_EDGE = 1080;
    public static final int THUMB_SIZE = 320;
    private static final float WEBP_QUALITY = 0.82f;

    /** Fits within 1080px on the long edge; smaller images are not enlarged. */
    public BufferedImage display(BufferedImage source) throws IOException {
        if (Math.max(source.getWidth(), source.getHeight()) <= DISPLAY_LONG_EDGE) {
            return source;
        }
        return Thumbnails.of(source).size(DISPLAY_LONG_EDGE, DISPLAY_LONG_EDGE).asBufferedImage();
    }

    /** A 320px square, center-cropped. */
    public BufferedImage thumb(BufferedImage source) throws IOException {
        return Thumbnails.of(source).size(THUMB_SIZE, THUMB_SIZE).crop(Positions.CENTER).asBufferedImage();
    }

    public byte[] webp(BufferedImage image) throws IOException {
        ImageWriter writer = new WebPImageWriterSpi().createWriterInstance(null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            WebPWriteParam param = new WebPWriteParam(writer.getLocale());
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(CompressionType.Lossy);
            param.setCompressionQuality(WEBP_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
