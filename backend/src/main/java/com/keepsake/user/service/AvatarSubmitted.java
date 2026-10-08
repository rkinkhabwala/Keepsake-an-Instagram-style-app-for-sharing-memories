package com.keepsake.user.service;

import java.util.UUID;

/** Published when a user picks a new avatar upload; processed after the transaction commits. */
public record AvatarSubmitted(UUID userId, UUID uploadId) {
}
