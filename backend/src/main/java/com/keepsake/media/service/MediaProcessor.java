package com.keepsake.media.service;

import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.entity.MediaUploadStatus;
import com.keepsake.media.entity.ProcessedImage;
import com.keepsake.media.repository.MediaUploadRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Processes one image upload: verify the stored object, check magic bytes against the declared type, read EXIF,
 * then write EXIF-free WebP renditions and a blurhash. Runs without an open transaction so slow image work never
 * holds a database connection.
 */
@Service
public class MediaProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);
    private static final String WEBP = "image/webp";

    private final MediaUploadRepository uploadRepository;
    private final MediaStorage storage;
    private final ExifReader exifReader;
    private final ImageDecoder decoder;
    private final ImageRenditions renditions;
    private final MeterRegistry meterRegistry;

    public MediaProcessor(
            MediaUploadRepository uploadRepository,
            MediaStorage storage,
            ExifReader exifReader,
            ImageDecoder decoder,
            ImageRenditions renditions,
            MeterRegistry meterRegistry
    ) {
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.exifReader = exifReader;
        this.decoder = decoder;
        this.renditions = renditions;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @return true when the upload is READY afterwards; failures are recorded on the upload, never thrown. Timed as
     *     {@code keepsake.media.processing} with outcome {@code ready} or {@code failed}.
     */
    public boolean process(UUID uploadId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean ready = processUntimed(uploadId);
        sample.stop(Timer.builder("keepsake.media.processing")
                .description("Time to verify and render one uploaded image")
                .tag("outcome", ready ? "ready" : "failed")
                .register(meterRegistry));
        return ready;
    }

    private boolean processUntimed(UUID uploadId) {
        Optional<MediaUpload> found = uploadRepository.findById(uploadId);
        if (found.isEmpty()) {
            log.warn("Media upload {} no longer exists; skipping processing", uploadId);
            return false;
        }
        MediaUpload upload = found.get();
        if (upload.getStatus() == MediaUploadStatus.READY) {
            return true;
        }
        try {
            upload.markReady(render(upload));
            uploadRepository.save(upload);
            return true;
        } catch (MediaProcessingException exception) {
            log.info("Media upload {} failed processing: {}", uploadId, exception.getMessage());
            return fail(upload, exception.getMessage());
        } catch (IOException | RuntimeException exception) {
            log.error("Media upload {} failed processing unexpectedly", uploadId, exception);
            return fail(upload, "Image could not be processed");
        }
    }

    private ProcessedImage render(MediaUpload upload) throws MediaProcessingException, IOException {
        byte[] original = readOriginal(upload);
        ImageType declared = ImageType.fromContentType(upload.getContentType())
                .orElseThrow(() -> new MediaProcessingException("Unsupported content type"));
        ImageType actual = ImageType.detect(original)
                .orElseThrow(() -> new MediaProcessingException("File is not a supported image"));
        if (actual != declared) {
            throw new MediaProcessingException("File content does not match its declared type");
        }

        ExifData exif = exifReader.read(original);
        BufferedImage image = decoder.decode(original, actual, exif.orientation());
        BufferedImage display = renditions.display(image);
        BufferedImage thumb = renditions.thumb(image);
        storage.write(upload.displayKeyFor(), renditions.webp(display), WEBP);
        storage.write(upload.thumbKeyFor(), renditions.webp(thumb), WEBP);
        return new ProcessedImage(
                upload.displayKeyFor(),
                upload.thumbKeyFor(),
                display.getWidth(),
                display.getHeight(),
                Blurhash.encode(thumb, 4, 3),
                exif.takenOn(),
                exif.latitude(),
                exif.longitude());
    }

    /** Reads the original after checking it exists and is no larger than the size the client declared. */
    byte[] readOriginal(MediaUpload upload) throws MediaProcessingException {
        long storedSize = storage.size(upload.getOriginalKey())
                .orElseThrow(() -> new MediaProcessingException("Uploaded file was not found"));
        if (storedSize > upload.getSizeBytes() || storedSize > MediaUploadService.MAX_IMAGE_BYTES) {
            throw new MediaProcessingException("Uploaded file is larger than declared");
        }
        return storage.read(upload.getOriginalKey());
    }

    private boolean fail(MediaUpload upload, String reason) {
        upload.markFailed(reason);
        uploadRepository.save(upload);
        return false;
    }
}
