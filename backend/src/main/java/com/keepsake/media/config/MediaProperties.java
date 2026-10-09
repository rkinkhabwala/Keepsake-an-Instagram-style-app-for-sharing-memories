package com.keepsake.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param heifDecoder       libheif CLI that converts HEIC uploads to PNG, invoked as {@code <cmd> in out.png}
 * @param heifInfo          libheif CLI that reports image sizes without decoding, invoked as {@code <cmd> in}
 * @param processingTimeout how long a memory may stay PROCESSING before it is marked FAILED so it can be retried
 */
@ConfigurationProperties(prefix = "keepsake.media")
public record MediaProperties(String heifDecoder, String heifInfo, Duration processingTimeout) {

    public MediaProperties {
        heifInfo = heifInfo == null || heifInfo.isBlank() ? "heif-info" : heifInfo;
        processingTimeout = processingTimeout == null ? Duration.ofMinutes(10) : processingTimeout;
    }
}
