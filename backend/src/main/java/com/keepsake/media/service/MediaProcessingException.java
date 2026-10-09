package com.keepsake.media.service;

/** A processing failure whose message is safe to store and show to the uploader. */
public class MediaProcessingException extends Exception {

    public MediaProcessingException(String message) {
        super(message);
    }

    public MediaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
