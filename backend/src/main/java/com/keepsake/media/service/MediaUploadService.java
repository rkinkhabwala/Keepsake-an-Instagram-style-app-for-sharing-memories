package com.keepsake.media.service;

import com.keepsake.common.error.ConflictException;
import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.media.dto.UploadMetadataDto;
import com.keepsake.media.dto.UploadUrlDto;
import com.keepsake.media.dto.UploadUrlRequest;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.entity.MediaUploadStatus;
import com.keepsake.media.repository.MediaUploadRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaUploadService {

    public static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    public static final int MAX_ITEMS = 10;
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime");

    private final MediaUploadRepository uploadRepository;
    private final MediaStorage storage;
    private final MediaProcessor processor;
    private final ExifReader exifReader;
    private final Clock clock;

    public MediaUploadService(
            MediaUploadRepository uploadRepository,
            MediaStorage storage,
            MediaProcessor processor,
            ExifReader exifReader,
            Clock clock
    ) {
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.processor = processor;
        this.exifReader = exifReader;
        this.clock = clock;
    }

    @Transactional
    public List<UploadUrlDto> createUploadUrls(UUID ownerId, List<UploadUrlRequest> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new InvalidRequestException("Request between 1 and " + MAX_ITEMS + " upload URLs");
        }
        items.forEach(MediaUploadService::validate);
        Instant expiresAt = clock.instant().plus(MediaStorage.UPLOAD_URL_LIFETIME);
        return items.stream().map(item -> {
            String contentType = item.contentType().trim().toLowerCase(java.util.Locale.ROOT);
            MediaUpload upload = uploadRepository.save(new MediaUpload(ownerId, contentType, item.sizeBytes()));
            return new UploadUrlDto(
                    upload.getId(),
                    storage.presignUpload(upload.getOriginalKey(), contentType, item.sizeBytes()).toString(),
                    "PUT",
                    Map.of("Content-Type", contentType),
                    expiresAt);
        }).toList();
    }

    /**
     * Locks the caller's PENDING uploads and moves them to PROCESSING, so each upload is used exactly once.
     * Unknown, foreign, or already-used ids are rejected without revealing which case applied.
     */
    @Transactional
    public List<MediaUpload> claimForProcessing(UUID ownerId, List<UUID> uploadIds) {
        Set<UUID> distinct = new LinkedHashSet<>(uploadIds);
        if (uploadIds.isEmpty() || distinct.size() != uploadIds.size()) {
            throw new InvalidRequestException("uploadIds must be distinct and non-empty");
        }
        Map<UUID, MediaUpload> found = uploadRepository.findAllByIdForUpdate(distinct).stream()
                .collect(Collectors.toMap(MediaUpload::getId, Function.identity(), (a, b) -> a, HashMap::new));
        for (UUID uploadId : uploadIds) {
            MediaUpload upload = found.get(uploadId);
            if (upload == null || !upload.getOwnerId().equals(ownerId)
                    || upload.getStatus() != MediaUploadStatus.PENDING) {
                throw new InvalidRequestException("Upload " + uploadId + " is not available");
            }
        }
        return uploadIds.stream().map(found::get).peek(MediaUpload::markProcessing).toList();
    }

    /**
     * EXIF hints for the create form: the date taken and GPS position. GPS is only ever offered to the uploader,
     * never attached to a memory automatically.
     */
    @Transactional(readOnly = true)
    public UploadMetadataDto metadata(UUID ownerId, UUID uploadId) {
        MediaUpload upload = uploadRepository.findById(uploadId)
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new NotFoundException("Upload not found"));
        if (upload.getStatus() == MediaUploadStatus.READY) {
            return new UploadMetadataDto(upload.getTakenOn(), upload.getGpsLat(), upload.getGpsLng());
        }
        byte[] original;
        try {
            original = processor.readOriginal(upload);
        } catch (MediaProcessingException exception) {
            throw new ConflictException(exception.getMessage());
        }
        if (ImageType.detect(original).isEmpty()) {
            throw new InvalidRequestException("File is not a supported image");
        }
        ExifData exif = exifReader.read(original);
        return new UploadMetadataDto(exif.takenOn(), exif.latitude(), exif.longitude());
    }

    private static void validate(UploadUrlRequest item) {
        if (item == null || item.contentType() == null) {
            throw new InvalidRequestException("contentType is required");
        }
        String contentType = item.contentType().trim().toLowerCase(java.util.Locale.ROOT);
        if (VIDEO_TYPES.contains(contentType)) {
            throw new InvalidRequestException("Video uploads are not supported yet");
        }
        if (ImageType.fromContentType(contentType).isEmpty()) {
            throw new InvalidRequestException("Unsupported content type; use JPEG, PNG, HEIC, or WebP");
        }
        if (item.sizeBytes() == null || item.sizeBytes() < 1 || item.sizeBytes() > MAX_IMAGE_BYTES) {
            throw new InvalidRequestException("Images must be between 1 byte and 20 MB");
        }
    }
}
