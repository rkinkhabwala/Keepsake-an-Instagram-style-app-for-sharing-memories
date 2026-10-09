package com.keepsake.user.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.error.UnauthenticatedException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.media.entity.MediaUploadStatus;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaUploadService;
import com.keepsake.user.dto.MeDto;
import com.keepsake.user.dto.UpdateProfileRequest;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.BlockRepository;
import com.keepsake.user.repository.FollowRepository;
import com.keepsake.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.keepsake.media.service.MediaJobs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserSummaries userSummaries;
    private final MediaUploadService uploadService;
    private final MediaUploadRepository uploadRepository;
    private final MediaJobs mediaJobs;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;

    public UserService(
            UserRepository userRepository,
            UserSummaries userSummaries,
            MediaUploadService uploadService,
            MediaUploadRepository uploadRepository,
            MediaJobs mediaJobs,
            FollowRepository followRepository,
            BlockRepository blockRepository
    ) {
        this.userRepository = userRepository;
        this.userSummaries = userSummaries;
        this.uploadService = uploadService;
        this.uploadRepository = uploadRepository;
        this.mediaJobs = mediaJobs;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
    }

    @Transactional(readOnly = true)
    public MeDto me(UUID userId) {
        return userSummaries.me(requireCurrent(userId));
    }

    @Transactional
    public MeDto updateMe(UUID userId, UpdateProfileRequest request) {
        User user = requireCurrent(userId);
        String displayName = user.getDisplayName();
        if (request.displayName() != null) {
            displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new InvalidRequestException("displayName must not be blank");
            }
        }
        String bio = user.getBio();
        if (request.bio() != null) {
            String trimmed = request.bio().trim();
            bio = trimmed.isEmpty() ? null : trimmed;
        }
        boolean isPrivate = request.isPrivate() == null ? user.isPrivate() : request.isPrivate();
        if (user.isPrivate() && !isPrivate) {
            // Going public: anyone still waiting for approval becomes a follower, as they would by following now.
            followRepository.acceptAllPending(userId);
        }
        user.updateProfile(displayName, bio, isPrivate);
        if (request.avatarUploadId() != null) {
            uploadService.claimForProcessing(userId, List.of(request.avatarUploadId()));
            user.requestAvatar(request.avatarUploadId());
            mediaJobs.submitAvatar(userId, request.avatarUploadId());
        }
        return userSummaries.me(userRepository.saveAndFlush(user));
    }

    /**
     * Switches the avatar to a processed upload's square thumb, if the upload is the user's, READY, and still the
     * avatar they most recently asked for (processing can finish out of order).
     */
    @Transactional
    public void applyAvatar(UUID userId, UUID uploadId) {
        userRepository.findById(userId)
                .filter(user -> uploadId.equals(user.getAvatarUploadId()))
                .ifPresent(user -> uploadRepository.findById(uploadId)
                .filter(upload -> upload.getOwnerId().equals(userId))
                .filter(upload -> upload.getStatus() == MediaUploadStatus.READY)
                .ifPresent(upload -> user.setAvatarKey(upload.getThumbKey())));
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * A user as seen by {@code viewerId}: when either has blocked the other, the user does not exist for the
     * viewer (404), so a block is never revealed.
     */
    @Transactional(readOnly = true)
    public User requireViewableByUsername(UUID viewerId, String username) {
        return viewable(viewerId, requireByUsername(username));
    }

    @Transactional(readOnly = true)
    public User requireViewableById(UUID viewerId, UUID userId) {
        return viewable(viewerId, requireById(userId));
    }

    private User viewable(UUID viewerId, User user) {
        if (!user.getId().equals(viewerId) && blockRepository.existsBetween(viewerId, user.getId())) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** The authenticated user; a valid token for a deleted account is treated as unauthenticated. */
    private User requireCurrent(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UnauthenticatedException::new);
    }
}
