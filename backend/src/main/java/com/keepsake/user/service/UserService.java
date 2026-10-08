package com.keepsake.user.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.error.UnauthenticatedException;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.user.dto.MeDto;
import com.keepsake.user.dto.UpdateProfileRequest;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MeDto me(UUID userId) {
        return MeDto.from(requireCurrent(userId));
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
        user.updateProfile(displayName, bio, isPrivate);
        return MeDto.from(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("User not found"));
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
