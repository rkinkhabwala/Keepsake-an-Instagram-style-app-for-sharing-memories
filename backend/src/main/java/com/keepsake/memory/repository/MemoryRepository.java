package com.keepsake.memory.repository;

import com.keepsake.memory.entity.Memory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memory m WHERE m.id = :id")
    Optional<Memory> findByIdForUpdate(UUID id);
}
