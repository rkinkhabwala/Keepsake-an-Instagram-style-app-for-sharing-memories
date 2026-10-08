package com.keepsake.user.service;

import com.keepsake.media.service.MediaStorage;
import com.keepsake.user.dto.MeDto;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.User;
import org.springframework.stereotype.Component;

/** Builds user DTOs, signing avatar URLs (the EXIF-free square thumb of the avatar upload). */
@Component
public class UserSummaries {

    private final MediaStorage storage;

    public UserSummaries(MediaStorage storage) {
        this.storage = storage;
    }

    public UserSummaryDto of(User user) {
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl(user));
    }

    public MeDto me(User user) {
        return new MeDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.isPrivate(),
                avatarUrl(user));
    }

    public String avatarUrl(User user) {
        return user.getAvatarKey() == null ? null : storage.presignDownload(user.getAvatarKey()).toString();
    }
}
