package com.keepsake.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param heifDecoder the libheif CLI used to convert HEIC uploads to PNG, invoked as {@code <cmd> in out.png} */
@ConfigurationProperties(prefix = "keepsake.media")
public record MediaProperties(String heifDecoder) {
}
