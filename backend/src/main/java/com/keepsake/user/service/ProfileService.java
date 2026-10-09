package com.keepsake.user.service;

import com.keepsake.memory.service.MemoryListService;
import com.keepsake.user.dto.ProfileDto;
import com.keepsake.user.entity.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserService userService;
    private final FollowService followService;
    private final UserSummaries userSummaries;
    private final MemoryListService memoryListService;

    public ProfileService(
            UserService userService,
            FollowService followService,
            UserSummaries userSummaries,
            MemoryListService memoryListService
    ) {
        this.userService = userService;
        this.followService = followService;
        this.userSummaries = userSummaries;
        this.memoryListService = memoryListService;
    }

    /** Profile header info is visible to every signed-in user, including for private accounts. */
    @Transactional(readOnly = true)
    public ProfileDto profile(UUID viewerId, String username) {
        User user = userService.requireByUsername(username);
        return new ProfileDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                userSummaries.avatarUrl(user),
                user.isPrivate(),
                memoryListService.visibleCount(viewerId, user.getId()),
                followService.followerCount(user.getId()),
                followService.followingCount(user.getId()),
                followService.relationship(viewerId, user.getId()));
    }
}
