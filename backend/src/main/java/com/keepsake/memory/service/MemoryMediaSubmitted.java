package com.keepsake.memory.service;

import java.util.List;
import java.util.UUID;

/** Published when a memory's uploads are ready to process; handled after the transaction commits. */
public record MemoryMediaSubmitted(UUID memoryId, List<UUID> uploadIds) {
}
