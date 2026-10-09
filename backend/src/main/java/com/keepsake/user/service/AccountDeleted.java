package com.keepsake.user.service;

import java.util.List;
import java.util.UUID;

/** Published when an account is deleted; its media objects are removed from storage after commit. */
public record AccountDeleted(UUID userId, List<String> storageKeys) {
}
