package com.keepsake.memory.repository;

import com.keepsake.memory.entity.MemoryMedia;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryMediaRepository extends JpaRepository<MemoryMedia, UUID> {

    List<MemoryMedia> findByMemoryIdOrderByPosition(UUID memoryId);

    List<MemoryMedia> findByMemoryIdInOrderByPosition(Collection<UUID> memoryIds);
}
