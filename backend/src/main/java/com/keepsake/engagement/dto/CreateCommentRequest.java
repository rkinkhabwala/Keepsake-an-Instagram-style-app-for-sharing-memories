package com.keepsake.engagement.dto;

import java.util.UUID;

/** @param parentId set to reply to a top-level comment; replies cannot be replied to */
public record CreateCommentRequest(String body, UUID parentId) {
}
