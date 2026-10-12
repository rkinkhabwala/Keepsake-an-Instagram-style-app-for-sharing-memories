package com.keepsake.notification.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidCursorException;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryDtoAssembler;
import com.keepsake.memory.service.MemoryService;
import com.keepsake.notification.dto.NotificationDto;
import com.keepsake.notification.entity.NotificationType;
import com.keepsake.notification.repository.NotificationRepository;
import com.keepsake.notification.repository.NotificationRepository.NotificationRow;
import com.keepsake.notification.service.NotificationEvents.NotificationEvent;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import com.keepsake.user.service.BlockService;
import com.keepsake.user.service.UserSummaries;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records notifications from events and delivers them live over STOMP to {@code /user/queue/notifications}.
 * Nobody is notified about their own actions, about an actor they are in a block with, or about a memory they
 * cannot see; listing applies the same rules again, so later blocks and visibility changes hide old ones too.
 */
@Service
public class NotificationService {

    public static final String QUEUE = "/queue/notifications";
    private static final Instant AFTER_EVERYTHING = Instant.parse("9999-12-31T00:00:00Z");
    private static final UUID MAX_UUID = new UUID(-1L, -1L);
    private static final int PREVIEW_LENGTH = 100;

    private final NotificationRepository repository;
    private final MemoryService memoryService;
    private final MemoryRepository memoryRepository;
    private final MemoryDtoAssembler memoryAssembler;
    private final BlockService blockService;
    private final UserRepository userRepository;
    private final UserSummaries userSummaries;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public NotificationService(
            NotificationRepository repository,
            MemoryService memoryService,
            MemoryRepository memoryRepository,
            MemoryDtoAssembler memoryAssembler,
            BlockService blockService,
            UserRepository userRepository,
            UserSummaries userSummaries,
            SimpMessagingTemplate messaging,
            Clock clock
    ) {
        this.repository = repository;
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
        this.memoryAssembler = memoryAssembler;
        this.blockService = blockService;
        this.userRepository = userRepository;
        this.userSummaries = userSummaries;
        this.messaging = messaging;
        this.clock = clock;
    }

    @Transactional
    public void record(NotificationEvent event) {
        UUID recipientId = event.recipientId();
        if (event.actorId() != null) {
            if (event.actorId().equals(recipientId) || blockService.isBlockedEitherWay(recipientId, event.actorId())) {
                return;
            }
        }
        if (event.memoryId() != null && !canSee(recipientId, event.memoryId())) {
            return;
        }
        Optional<UUID> recorded = event.type() == NotificationType.LIKE
                ? repository.addLike(recipientId, event.actorId(), event.memoryId(), clock.instant())
                : repository.add(recipientId, event.type(), event.actorId(), event.memoryId(), event.commentId(),
                        event.eventId(), clock.instant());
        recorded.ifPresent(id -> deliverAfterCommit(recipientId, id));
    }

    /** A notification not made from an event (On This Day), delivered once the caller's transaction commits. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordOnThisDay(UUID recipientId, UUID memoryId) {
        repository.add(recipientId, NotificationType.ON_THIS_DAY, null, memoryId, null, null, clock.instant())
                .ifPresent(id -> deliverAfterCommit(recipientId, id));
    }

    /** The cursor is opaque: {@code <last change in epoch microseconds>.<notification id>}. */
    @Transactional(readOnly = true)
    public CursorPage<NotificationDto> list(UUID viewerId, String cursor, Integer limit) {
        int pageSize = CursorPageRequest.parse(null, limit).limit();
        Instant before = AFTER_EVERYTHING;
        UUID beforeId = MAX_UUID;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\.", 2);
            try {
                before = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException malformed) {
                throw new InvalidCursorException();
            }
        }
        List<NotificationRow> rows = repository.findVisible(viewerId, before, beforeId, pageSize + 1);
        boolean more = rows.size() > pageSize;
        List<NotificationRow> items = more ? rows.subList(0, pageSize) : rows;
        String next = more
                ? ChronoUnit.MICROS.between(Instant.EPOCH, items.getLast().updatedAt()) + "." + items.getLast().id()
                : null;
        return new CursorPage<>(toDtos(items), next);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID viewerId) {
        return repository.countUnread(viewerId);
    }

    @Transactional
    public void markAllRead(UUID viewerId) {
        repository.markAllRead(viewerId, clock.instant());
    }

    private boolean canSee(UUID viewerId, UUID memoryId) {
        try {
            memoryService.requireVisible(viewerId, memoryId);
            return true;
        } catch (NotFoundException hidden) {
            return false;
        }
    }

    private void deliverAfterCommit(UUID recipientId, UUID notificationId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(recipientId, notificationId);
            }
        });
    }

    @Transactional(readOnly = true)
    void deliver(UUID recipientId, UUID notificationId) {
        repository.findVisible(recipientId, notificationId).ifPresent(row ->
                messaging.convertAndSendToUser(recipientId.toString(), QUEUE, toDtos(List.of(row)).getFirst()));
    }

    private List<NotificationDto> toDtos(List<NotificationRow> rows) {
        Map<UUID, UserSummaryDto> actors = userRepository.findAllById(
                        rows.stream().map(NotificationRow::actorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, userSummaries::of));
        Map<UUID, MemoryGridItemDto> memories = memoryAssembler.toGridItems(memoryRepository.findAllById(
                        rows.stream().map(NotificationRow::memoryId).filter(Objects::nonNull).distinct().toList()))
                .stream().collect(Collectors.toMap(MemoryGridItemDto::id, Function.identity()));
        return rows.stream().map(row -> new NotificationDto(
                row.id(),
                row.type(),
                row.actorId() == null ? null : actors.get(row.actorId()),
                Math.max(0, row.actorCount() - 1),
                row.memoryId(),
                row.memoryId() == null || !memories.containsKey(row.memoryId())
                        ? null : memories.get(row.memoryId()).thumbUrl(),
                preview(row.commentBody()),
                row.read(),
                row.updatedAt())).toList();
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH - 1) + "…";
    }
}
