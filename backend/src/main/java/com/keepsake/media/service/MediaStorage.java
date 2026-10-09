package com.keepsake.media.service;

import com.keepsake.media.config.StorageProperties;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Object storage access. The server never proxies uploads: clients PUT and GET through presigned URLs. */
@Service
public class MediaStorage {

    public static final Duration UPLOAD_URL_LIFETIME = Duration.ofMinutes(15);
    public static final Duration DOWNLOAD_URL_LIFETIME = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public MediaStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    /** A PUT URL whose signature covers the content type and exact length the client declared. */
    public URL presignUpload(String key, String contentType, long sizeBytes) {
        return presigner.presignPutObject(request -> request
                .signatureDuration(UPLOAD_URL_LIFETIME)
                .putObjectRequest(put -> put
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(sizeBytes)))
                .url();
    }

    /** A short-lived GET URL. Only ever call this for derived (EXIF-free) keys, never originals. */
    public URL presignDownload(String key) {
        return presigner.presignGetObject(request -> request
                .signatureDuration(DOWNLOAD_URL_LIFETIME)
                .getObjectRequest(get -> get.bucket(bucket).key(key)))
                .url();
    }

    public Optional<Long> size(String key) {
        try {
            return Optional.of(s3Client.headObject(request -> request.bucket(bucket).key(key)).contentLength());
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public byte[] read(String key) {
        return s3Client.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray();
    }

    /** The first {@code length} bytes of an object (or all of it, if shorter). */
    public byte[] readPrefix(String key, int length) {
        return s3Client.getObjectAsBytes(request -> request.bucket(bucket).key(key).range("bytes=0-" + (length - 1)))
                .asByteArray();
    }

    public void write(String key, byte[] bytes, String contentType) {
        s3Client.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromBytes(bytes));
    }
}
