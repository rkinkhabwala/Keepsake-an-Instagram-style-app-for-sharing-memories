package com.keepsake.engagement.repository;

import com.keepsake.engagement.entity.Save;
import com.keepsake.engagement.entity.UserMemoryId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Idempotent writes; the returned row count tells the caller whether anything changed. */
public interface SaveRepository extends JpaRepository<Save, UserMemoryId> {

    @Modifying
    @Query(value = """
            INSERT INTO saves (user_id, memory_id, created_at, updated_at) VALUES (:userId, :memoryId, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID userId, UUID memoryId);

    @Modifying
    @Query(value = "DELETE FROM saves WHERE user_id = :userId AND memory_id = :memoryId", nativeQuery = true)
    int deleteIfPresent(UUID userId, UUID memoryId);

    @Query("SELECT x.id.memoryId FROM Save x WHERE x.id.userId = :userId AND x.id.memoryId IN :memoryIds")
    List<UUID> findMemoryIds(UUID userId, Collection<UUID> memoryIds);
}
