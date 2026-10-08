package com.keepsake.memory.service;

import com.keepsake.media.service.MediaStorage;
import com.keepsake.memory.dto.LocationDto;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MemoryMediaDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.entity.MemoryMedia;
import com.keepsake.memory.repository.MemoryMediaRepository;
import com.keepsake.memory.repository.MemoryPersonRepository;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import com.keepsake.user.service.UserSummaries;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds memory DTOs. Callers must already have checked access through {@link MemoryAccessPolicy}. */
@Component
public class MemoryDtoAssembler {

    private final MemoryMediaRepository mediaRepository;
    private final MemoryPersonRepository personRepository;
    private final MediaStorage storage;
    private final UserSummaries userSummaries;
    private final UserRepository userRepository;

    public MemoryDtoAssembler(
            MemoryMediaRepository mediaRepository,
            MemoryPersonRepository personRepository,
            MediaStorage storage,
            UserSummaries userSummaries,
            UserRepository userRepository
    ) {
        this.mediaRepository = mediaRepository;
        this.personRepository = personRepository;
        this.storage = storage;
        this.userSummaries = userSummaries;
        this.userRepository = userRepository;
    }

    /** Batched version of {@link #toDto} for lists: a fixed number of queries regardless of page size. */
    public List<MemoryDto> toDtos(List<Memory> memories) {
        if (memories.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = memories.stream().map(Memory::getId).toList();
        Map<UUID, UserSummaryDto> authors = userRepository.findAllById(
                        memories.stream().map(Memory::getAuthorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, userSummaries::of));
        Map<UUID, List<MemoryMediaDto>> media = mediaRepository.findByMemoryIdInOrderByPosition(ids).stream()
                .collect(Collectors.groupingBy(MemoryMedia::getMemoryId,
                        Collectors.mapping(this::media, Collectors.toList())));
        Map<UUID, List<UserSummaryDto>> tagged = personRepository.findTaggedUsersByMemoryIds(ids).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0],
                        Collectors.mapping(row -> userSummaries.of((User) row[1]), Collectors.toList())));
        return memories.stream().map(memory -> new MemoryDto(
                memory.getId(),
                authors.get(memory.getAuthorId()),
                memory.getCaption(),
                memory.getOccurredOn(),
                location(memory),
                memory.getVisibility(),
                memory.getStatus(),
                memory.getLikeCount(),
                memory.getCommentCount(),
                media.getOrDefault(memory.getId(), List.of()),
                tagged.getOrDefault(memory.getId(), List.of()),
                memory.getCreatedAt())).toList();
    }

    /** Grid tiles: the first photo's thumb only. */
    public List<MemoryGridItemDto> toGridItems(List<Memory> memories) {
        if (memories.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<MemoryMedia>> media = mediaRepository.findByMemoryIdInOrderByPosition(
                        memories.stream().map(Memory::getId).toList()).stream()
                .collect(Collectors.groupingBy(MemoryMedia::getMemoryId));
        return memories.stream().map(memory -> {
            List<MemoryMedia> items = media.getOrDefault(memory.getId(), List.of());
            MemoryMedia cover = items.isEmpty() ? null : items.getFirst();
            return new MemoryGridItemDto(
                    memory.getId(),
                    cover == null || !cover.isReady() ? null : storage.presignDownload(cover.getThumbKey()).toString(),
                    cover == null ? null : cover.getBlurhash(),
                    items.size(),
                    memory.getOccurredOn());
        }).toList();
    }

    public MemoryDto toDto(Memory memory, User author) {
        return new MemoryDto(
                memory.getId(),
                userSummaries.of(author),
                memory.getCaption(),
                memory.getOccurredOn(),
                location(memory),
                memory.getVisibility(),
                memory.getStatus(),
                memory.getLikeCount(),
                memory.getCommentCount(),
                mediaRepository.findByMemoryIdOrderByPosition(memory.getId()).stream().map(this::media).toList(),
                personRepository.findTaggedUsers(memory.getId()).stream().map(userSummaries::of).toList(),
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
