package com.keepsake.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param heifDecoder       libheif CLI that converts HEIC uploads to PNG, invoked as {@code <cmd> in out.png}
 * @param heifInfo          libheif CLI that reports image sizes without decoding, invoked as {@code <cmd> in}
 * @param processingTimeout how long a memory may stay PROCESSING before it is marked FAILED so it can be retried
 * @param ffmpeg            FFmpeg command, for video posters and transcoding
 * @param ffprobe           ffprobe command, for reading video headers
 */
@ConfigurationProperties(prefix = "keepsake.media")
public record MediaProperties(
        String heifDecoder,
        String heifInfo,
        Duration processingTimeout,
        String ffmpeg,
        String ffprobe
) {

    public MediaProperties {
        ffmpeg = ffmpeg == null || ffmpeg.isBlank() ? "ffmpeg" : ffmpeg;
        ffprobe = ffprobe == null || ffprobe.isBlank() ? "ffprobe" : ffprobe;
        heifInfo = heifInfo == null || heifInfo.isBlank() ? "heif-info" : heifInfo;
        processingTimeout = processingTimeout == null ? Duration.ofMinutes(10) : processingTimeout;
    }
}
