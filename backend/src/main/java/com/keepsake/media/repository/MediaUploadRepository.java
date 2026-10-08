package com.keepsake.media.repository;

import com.keepsake.media.entity.MediaUpload;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM MediaUpload u WHERE u.id IN :ids")
    List<MediaUpload> findAllByIdForUpdate(Collection<UUID> ids);
}
