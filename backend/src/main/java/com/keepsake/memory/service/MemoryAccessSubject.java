package com.keepsake.memory.service;

import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import java.util.UUID;

/** The memory facts {@link MemoryAccessPolicy} needs, independent of how the memory was loaded. */
public record MemoryAccessSubject(
        UUID authorId,
        boolean authorPrivate,
        Visibility visibility,
        MemoryStatus status,
        boolean deleted
) {
}
