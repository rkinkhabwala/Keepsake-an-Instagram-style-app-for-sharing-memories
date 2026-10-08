package com.keepsake.memory.service;

import com.keepsake.media.service.MediaStorage;
import com.keepsake.memory.dto.LocationDto;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MemoryMediaDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.entity.MemoryMedia;
import com.keepsake.memory.repository.MemoryMediaRepository;
import com.keepsake.memory.repository.MemoryPersonRepository;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.User;
import org.springframework.stereotype.Component;

/** Builds memory DTOs. Callers must already have checked access through {@link MemoryAccessPolicy}. */
@Component
public class MemoryDtoAssembler {

    private final MemoryMediaRepository mediaRepository;
    private final MemoryPersonRepository personRepository;
    private final MediaStorage storage;

    public MemoryDtoAssembler(
            MemoryMediaRepository mediaRepository,
            MemoryPersonRepository personRepository,
            MediaStorage storage
    ) {
        this.mediaRepository = mediaRepository;
        this.personRepository = personRepository;
        this.storage = storage;
    }

    public MemoryDto toDto(Memory memory, User author) {
        return new MemoryDto(
                memory.getId(),
                UserSummaryDto.from(author),
                memory.getCaption(),
                memory.getOccurredOn(),
                location(memory),
                memory.getVisibility(),
                memory.getStatus(),
                memory.getLikeCount(),
                memory.getCommentCount(),
                mediaRepository.findByMemoryIdOrderByPosition(memory.getId()).stream().map(this::media).toList(),
                personRepository.findTaggedUsers(memory.getId()).stream().map(UserSummaryDto::from).toList(),
                memory.getCreatedAt());
    }

    public MemoryMediaDto media(MemoryMedia media) {
        return new MemoryMediaDto(
                media.getPosition(),
                media.getType(),
                media.isReady() ? storage.presignDownload(media.getDisplayKey()).toString() : null,
                media.isReady() ? storage.presignDownload(media.getThumbKey()).toString() : null,
                media.getWidth(),
                media.getHeight(),
                media.getBlurhash());
    }

    static LocationDto location(Memory memory) {
        if (memory.getLocationName() == null && memory.getLat() == null) {
            return null;
        }
        return new LocationDto(memory.getLocationName(), memory.getLat(), memory.getLng());
    }
}
