package com.keepsake.user.service;

import com.keepsake.common.error.ForbiddenException;
import com.keepsake.common.error.UnauthenticatedException;
import com.keepsake.engagement.service.EngagementCounters;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaStorage;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.AccountDeletionRepository;
import com.keepsake.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes an account (spec §10): memories, media objects, likes, comments, saves, follows, close friends, tags,
 * and tokens. Database rows go in one transaction; storage objects are removed once it commits, so a failed
 * deletion never leaves an account whose photos are already gone.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final AccountDeletionRepository deletionRepository;
    private final MediaUploadRepository uploadRepository;
    private final MediaStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final EngagementCounters counters;

    public AccountDeletionService(
            UserRepository userRepository,
            AccountDeletionRepository deletionRepository,
            MediaUploadRepository uploadRepository,
            MediaStorage storage,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            EngagementCounters counters
    ) {
        this.userRepository = userRepository;
        this.deletionRepository = deletionRepository;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.counters = counters;
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(UnauthenticatedException::new);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ForbiddenException("Password is incorrect");
        }
        // Every object an upload can have produced, whether or not processing recorded it (avatars included).
        List<String> keys = uploadRepository.findAllByOwnerId(userId).stream()
                .flatMap(AccountDeletionService::storageKeys)
                .toList();
        List<UUID> engaged = deletionRepository.findMemoriesWithEngagementBy(userId);
        deletionRepository.deleteMemories(userId);
        deletionRepository.deleteUser(userId);
        events.publishEvent(new AccountDeleted(userId, keys, engaged));
    }

    @TransactionalEventListener
    public void removeMedia(AccountDeleted event) {
        // The user's own memories are gone; recompute counts on the others they had engaged with.
        counters.reconcileNow(event.engagedMemoryIds());
        try {
            storage.deleteAll(event.storageKeys());
        } catch (RuntimeException exception) {
            log.error("Account {} was deleted but {} media objects could not be removed from storage",
                    event.userId(), event.storageKeys().size(), exception);
        }
    }

    private static Stream<String> storageKeys(MediaUpload upload) {
        return Stream.of(upload.getOriginalKey(), upload.displayKeyFor(), upload.thumbKeyFor(), upload.videoKeyFor());
    }
}
