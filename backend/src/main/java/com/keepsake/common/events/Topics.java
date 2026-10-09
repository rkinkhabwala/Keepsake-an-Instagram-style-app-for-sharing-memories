package com.keepsake.common.events;

/** Kafka topics. Failed records go to the same name with a {@code .DLT} suffix after retries. */
public final class Topics {

    /** A memory's uploads are ready to process; keyed by memory id. */
    public static final String MEMORY_MEDIA = "keepsake.media.memory";
    /** An avatar upload is ready to process; keyed by user id. */
    public static final String AVATAR_MEDIA = "keepsake.media.avatar";
    /** Follows and follow requests, for notifications; keyed by recipient id. */
    public static final String SOCIAL_EVENTS = "keepsake.social.events";
    /** Likes, comments, and tags on memories, for notifications; keyed by recipient id. */
    public static final String MEMORY_EVENTS = "keepsake.memory.events";

    private Topics() {
    }
}
