package com.keepsake.memory.repository;

import com.keepsake.memory.entity.MemoryPerson;
import com.keepsake.memory.entity.MemoryPersonId;
import com.keepsake.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemoryPersonRepository extends JpaRepository<MemoryPerson, MemoryPersonId> {

    @Query("""
            SELECT u FROM MemoryPerson p, User u
            WHERE u.id = p.id.userId AND p.id.memoryId = :memoryId
            ORDER BY u.username
            """)
    List<User> findTaggedUsers(UUID memoryId);

    /** Rows of [memoryId, User] for every tagged person on the given memories. */
    @Query("""
            SELECT p.id.memoryId, u FROM MemoryPerson p, User u
            WHERE u.id = p.id.userId AND p.id.memoryId IN :memoryIds
            ORDER BY u.username
            """)
    List<Object[]> findTaggedUsersByMemoryIds(java.util.Collection<UUID> memoryIds);

    @Modifying
    @Query("DELETE FROM MemoryPerson p WHERE p.id.memoryId = :memoryId")
    void deleteByMemoryId(UUID memoryId);
}
