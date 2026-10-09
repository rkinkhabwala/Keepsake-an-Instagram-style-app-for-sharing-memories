package com.keepsake.user.service;

import java.util.List;
import java.util.UUID;

/**
 * Published when an account is deleted. After commit its media objects are removed from storage and the counts of
 * memories it had liked or commented on are recomputed.
 */
public record AccountDeleted(UUID userId, List<String> storageKeys, List<UUID> engagedMemoryIds) {
}
