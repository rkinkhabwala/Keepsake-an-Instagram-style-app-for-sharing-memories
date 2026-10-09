package com.keepsake.memory.service;

import com.keepsake.common.error.ConflictException;
import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.entity.MediaUploadStatus;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaUploadService;
import com.keepsake.memory.dto.CreateMemoryRequest;
import com.keepsake.memory.dto.LocationDto;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.dto.MemoryStatusDto;
import com.keepsake.memory.dto.UpdateMemoryRequest;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.entity.MemoryMedia;
import com.keepsake.memory.entity.MemoryPerson;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.repository.MemoryMediaRepository;
import com.keepsake.memory.repository.MemoryPersonRepository;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.user.entity.User;
import com.keepsake.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemoryService {

    static final int MAX_CAPTION_LENGTH = 2200;
    static final int MAX_LOCATION_NAME_LENGTH = 200;
    static final int MAX_MEDIA = 10;
    static final int MAX_TAGGED = 20;
    private static final LocalDate EARLIEST_OCCURRED_ON = LocalDate.of(1900, 1, 1);

    private final MemoryRepository memoryRepository;
    private final MemoryMediaRepository mediaRepository;
    private final MemoryPersonRepository personRepository;
    private final MediaUploadRepository uploadRepository;
    private final MediaUploadService uploadService;
    private final UserService userService;
    private final MemoryAccessPolicy accessPolicy;
    private final MemoryDtoAssembler assembler;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public MemoryService(
            MemoryRepository memoryRepository,
            MemoryMediaRepository mediaRepository,
            MemoryPersonRepository personRepository,
            MediaUploadRepository uploadRepository,
            MediaUploadService uploadService,
            UserService userService,
            MemoryAccessPolicy accessPolicy,
            MemoryDtoAssembler assembler,
            ApplicationEventPublisher events,
            Clock clock,
            TransactionTemplate transactions
    ) {
        this.memoryRepository = memoryRepository;
        this.mediaRepository = mediaRepository;
        this.personRepository = personRepository;
        this.uploadRepository = uploadRepository;
        this.uploadService = uploadService;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.assembler = assembler;
        this.events = events;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * Creates the memory in PROCESSING; its media is processed asynchronously after commit. When occurredOn is
     * omitted it defaults to the first photo's EXIF date (if plausible), else today. That date is read before the
     * transaction starts, so no database connection or row lock is held during storage I/O.
     */
    public MemoryStatusDto create(UUID authorId, CreateMemoryRequest request) {
        if (request.visibility() == null) {
            throw new InvalidRequestException("visibility is required");
        }
        List<UUID> uploadIds = request.uploadIds() == null ? List.of() : request.uploadIds();
        if (uploadIds.isEmpty() || uploadIds.size() > MAX_MEDIA) {
            throw new InvalidRequestException("A memory needs between 1 and " + MAX_MEDIA + " photos");
        }
        String caption = caption(request.caption());
        LocationDto location = location(request.location());
        Set<UUID> tagged = taggedUserIds(authorId, request.taggedUsernames());
        LocalDate occurredOn = request.occurredOn() != null
                ? validOccurredOn(request.occurredOn())
                : uploadService.peekTakenOn(authorId, uploadIds.getFirst())
                        .filter(this::isPlausibleOccurredOn)
                        .orElse(LocalDate.now(clock));
        return transactions.execute(status ->
                insert(authorId, request, uploadIds, caption, location, tagged, occurredOn));
    }

    private MemoryStatusDto insert(
            UUID authorId,
            CreateMemoryRequest request,
            List<UUID> uploadIds,
            String caption,
            LocationDto location,
            Set<UUID> tagged,
            LocalDate occurredOn
    ) {
        List<MediaUpload> uploads = uploadService.claimForProcessing(authorId, uploadIds);

        Memory memory = new Memory(authorId, occurredOn, request.visibility());
        memory.setCaption(caption);
        applyLocation(memory, location);
        memoryRepository.save(memory);
        for (int position = 0; position < uploads.size(); position++) {
            mediaRepository.save(new MemoryMedia(memory.getId(), uploads.get(position), position));
        }
        tagged.forEach(userId -> personRepository.save(new MemoryPerson(memory.getId(), userId)));
        events.publishEvent(new MemoryMediaSubmitted(memory.getId(), uploadIds));
        return new MemoryStatusDto(memory.getId(), memory.getStatus());
    }

    @Transactional(readOnly = true)
    public MemoryDto get(UUID viewerId, UUID memoryId) {
        Memory memory = memoryRepository.findById(memoryId).orElseThrow(MemoryService::notFound);
        User author = userService.requireById(memory.getAuthorId());
        accessPolicy.requireOpenable(viewerId, subject(memory, author));
        return assembler.toDtos(viewerId, List.of(memory)).getFirst();
    }

    /** For engagement: the memory, if the viewer may see it (READY and allowed by the policy), else 404. */
    @Transactional(readOnly = true)
    public Memory requireVisible(UUID viewerId, UUID memoryId) {
        Memory memory = memoryRepository.findById(memoryId).orElseThrow(MemoryService::notFound);
        accessPolicy.requireVisible(viewerId, subject(memory, userService.requireById(memory.getAuthorId())));
        return memory;
    }

    @Transactional
    public MemoryDto update(UUID viewerId, UUID memoryId, UpdateMemoryRequest request) {
        Memory memory = memoryRepository.findByIdForUpdate(memoryId).orElseThrow(MemoryService::notFound);
        User author = userService.requireById(memory.getAuthorId());
        accessPolicy.requireAuthor(viewerId, subject(memory, author));

        if (request.captionPresent()) {
            memory.setCaption(caption(request.caption()));
        }
        if (request.occurredOn() != null) {
            memory.setOccurredOn(validOccurredOn(request.occurredOn()));
        }
        if (request.locationPresent()) {
            applyLocation(memory, location(request.location()));
        }
        if (request.visibility() != null) {
            memory.setVisibility(request.visibility());
        }
        if (request.taggedUsernames() != null) {
            Set<UUID> tagged = taggedUserIds(viewerId, request.taggedUsernames());
            personRepository.deleteByMemoryId(memoryId);
            personRepository.flush();
            tagged.forEach(userId -> personRepository.save(new MemoryPerson(memoryId, userId)));
        }
        memoryRepository.saveAndFlush(memory);
        return assembler.toDtos(viewerId, List.of(memory)).getFirst();
    }

    /** Soft delete: the memory disappears everywhere at once, while its rows remain for account deletion. */
    @Transactional
    public void delete(UUID viewerId, UUID memoryId) {
        Memory memory = memoryRepository.findByIdForUpdate(memoryId).orElseThrow(MemoryService::notFound);
        accessPolicy.requireAuthor(viewerId, subject(memory, userService.requireById(memory.getAuthorId())));
        memory.softDelete(clock.instant());
    }

    /** Re-runs processing for a FAILED memory; uploads that already succeeded are not reprocessed. */
    @Transactional
    public MemoryStatusDto retry(UUID viewerId, UUID memoryId) {
        Memory memory = memoryRepository.findByIdForUpdate(memoryId).orElseThrow(MemoryService::notFound);
        accessPolicy.requireAuthor(viewerId, subject(memory, userService.requireById(memory.getAuthorId())));
        if (memory.getStatus() != MemoryStatus.FAILED) {
            throw new ConflictException("Only a failed memory can be retried");
        }
        List<UUID> uploadIds = mediaRepository.findByMemoryIdOrderByPosition(memoryId).stream()
                .map(MemoryMedia::getUploadId).toList();
        uploadRepository.findAllById(uploadIds).stream()
                .filter(upload -> upload.getStatus() == MediaUploadStatus.FAILED)
                .forEach(MediaUpload::markProcessing);
        memory.setStatus(MemoryStatus.PROCESSING);
        events.publishEvent(new MemoryMediaSubmitted(memoryId, uploadIds));
        return new MemoryStatusDto(memoryId, memory.getStatus());
    }

    /** Called when processing ends: READY only if every upload produced its renditions, otherwise FAILED. */
    @Transactional
    public void completeProcessing(UUID memoryId) {
        Memory memory = memoryRepository.findByIdForUpdate(memoryId).orElse(null);
        if (memory == null) {
            return;
        }
        List<MemoryMedia> media = mediaRepository.findByMemoryIdOrderByPosition(memoryId);
        Map<UUID, MediaUpload> uploads = uploadRepository.findAllById(
                        media.stream().map(MemoryMedia::getUploadId).toList()).stream()
                .collect(Collectors.toMap(MediaUpload::getId, Function.identity()));
        boolean allReady = true;
        for (MemoryMedia item : media) {
            MediaUpload upload = uploads.get(item.getUploadId());
            if (upload != null && upload.getStatus() == MediaUploadStatus.READY) {
                item.applyRenditions(upload);
            } else {
                allReady = false;
            }
        }
        memory.setStatus(allReady && !media.isEmpty() ? MemoryStatus.READY : MemoryStatus.FAILED);
    }

    /**
     * Marks memories that have been PROCESSING for longer than {@code timeout} as FAILED, so their authors can
     * retry them. Processing can be lost if the server restarts mid-way or the async queue is full.
     *
     * @return how many memories were failed
     */
    @Transactional
    public int failStaleProcessing(java.time.Duration timeout) {
        List<Memory> stale = memoryRepository.findStaleProcessing(clock.instant().minus(timeout));
        stale.forEach(memory -> memory.setStatus(MemoryStatus.FAILED));
        return stale.size();
    }

    static MemoryAccessSubject subject(Memory memory, User author) {
        return new MemoryAccessSubject(
                memory.getAuthorId(),
                author.isPrivate(),
                memory.getVisibility(),
                memory.getStatus(),
                memory.isDeleted());
    }

    private Set<UUID> taggedUserIds(UUID authorId, List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Set.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String username : usernames) {
            if (username == null || username.isBlank()) {
                throw new InvalidRequestException("taggedUsernames must not contain blanks");
            }
            distinct.add(username.trim().toLowerCase(Locale.ROOT));
        }
        if (distinct.size() > MAX_TAGGED) {
            throw new InvalidRequestException("You can tag at most " + MAX_TAGGED + " people");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String username : distinct) {
            User user;
            try {
                // Someone in a block with the author is as unknown to them as a username that does not exist.
                user = userService.requireViewableByUsername(authorId, username);
            } catch (NotFoundException missing) {
                throw new InvalidRequestException("Unknown user @" + username);
            }
            if (user.getId().equals(authorId)) {
                throw new InvalidRequestException("You cannot tag yourself");
            }
            ids.add(user.getId());
        }
        return ids;
    }

    private static String caption(String caption) {
        if (caption == null) {
            return null;
        }
        String trimmed = caption.strip();
        if (trimmed.length() > MAX_CAPTION_LENGTH) {
            throw new InvalidRequestException("caption must be at most " + MAX_CAPTION_LENGTH + " characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Normalises a location; returns null when it carries neither a name nor coordinates. */
    private static LocationDto location(LocationDto location) {
        if (location == null) {
            return null;
        }
        String name = location.name() == null ? null : location.name().strip();
        if (name != null && name.isEmpty()) {
            name = null;
        }
        if (name != null && name.length() > MAX_LOCATION_NAME_LENGTH) {
            throw new InvalidRequestException(
                    "location name must be at most " + MAX_LOCATION_NAME_LENGTH + " characters");
        }
        Double latitude = location.latitude();
        Double longitude = location.longitude();
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidRequestException("latitude and longitude must be given together");
        }
        if (latitude != null && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
            throw new InvalidRequestException("coordinates are out of range");
        }
        if (name == null && latitude == null) {
            return null;
        }
        return new LocationDto(name, latitude, longitude);
    }

    private static void applyLocation(Memory memory, LocationDto location) {
        if (location == null) {
            memory.setLocation(null, null, null);
        } else {
            memory.setLocation(location.name(), location.latitude(), location.longitude());
        }
    }

    /** Allows one day of slack past today so users ahead of UTC can still pick their own today. */
    private boolean isPlausibleOccurredOn(LocalDate occurredOn) {
        return !occurredOn.isBefore(EARLIEST_OCCURRED_ON) && !occurredOn.isAfter(LocalDate.now(clock).plusDays(1));
    }

    private LocalDate validOccurredOn(LocalDate occurredOn) {
        if (!isPlausibleOccurredOn(occurredOn)) {
            throw new InvalidRequestException("occurredOn must be between 1900-01-01 and today");
        }
        return occurredOn;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Memory not found");
    }
}
