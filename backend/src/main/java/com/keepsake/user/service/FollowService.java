package com.keepsake.user.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.user.dto.Relationship;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.Follow;
import com.keepsake.user.entity.FollowId;
import com.keepsake.user.entity.FollowStatus;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.FollowRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserService userService;
    private final UserSummaries userSummaries;

    public FollowService(FollowRepository followRepository, UserService userService, UserSummaries userSummaries) {
        this.followRepository = followRepository;
        this.userService = userService;
        this.userSummaries = userSummaries;
    }

    /** Following a private account creates a PENDING request; a public account is followed immediately. */
    @Transactional
    public Relationship follow(UUID viewerId, String username) {
        User target = userService.requireByUsername(username);
        if (target.getId().equals(viewerId)) {
            throw new InvalidRequestException("You cannot follow yourself");
        }
        FollowStatus status = target.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        followRepository.insertIfAbsent(viewerId, target.getId(), status.name());
        if (!target.isPrivate()) {
            // A request left over from when the account was private no longer needs approval.
            followRepository.acceptPending(viewerId, target.getId());
        }
        return relationship(viewerId, target.getId());
    }

    /** Removes an accepted follow or cancels a pending request; a no-op when neither exists. */
    @Transactional
    public void unfollow(UUID viewerId, String username) {
        User target = userService.requireByUsername(username);
        followRepository.deleteById(new FollowId(viewerId, target.getId()));
    }

    @Transactional
    public void acceptRequest(UUID ownerId, UUID requesterId) {
        if (followRepository.acceptPending(requesterId, ownerId) == 0) {
            throw new NotFoundException("Follow request not found");
        }
    }

    @Transactional
    public void declineRequest(UUID ownerId, UUID requesterId) {
        if (followRepository.deleteWithStatus(requesterId, ownerId, FollowStatus.PENDING) == 0) {
            throw new NotFoundException("Follow request not found");
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummaryDto> pendingRequests(UUID ownerId, CursorPageRequest page) {
        return toPage(followRepository.findFollowers(
                ownerId, FollowStatus.PENDING, page.cursorOrMax(), page.lookaheadLimit()), page);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummaryDto> followers(UUID viewerId, String username, CursorPageRequest page) {
        User owner = requireViewableAccount(viewerId, username);
        return toPage(followRepository.findFollowers(
                owner.getId(), FollowStatus.ACCEPTED, page.cursorOrMax(), page.lookaheadLimit()), page);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummaryDto> following(UUID viewerId, String username, CursorPageRequest page) {
        User owner = requireViewableAccount(viewerId, username);
        return toPage(followRepository.findFollowees(
                owner.getId(), FollowStatus.ACCEPTED, page.cursorOrMax(), page.lookaheadLimit()), page);
    }

    @Transactional(readOnly = true)
    public Relationship relationship(UUID viewerId, UUID ownerId) {
        if (viewerId.equals(ownerId)) {
            return Relationship.SELF;
        }
        return followStatus(viewerId, ownerId)
                .map(status -> status == FollowStatus.ACCEPTED ? Relationship.FOLLOWING : Relationship.REQUESTED)
                .orElse(Relationship.NONE);
    }

    @Transactional(readOnly = true)
    public Optional<FollowStatus> followStatus(UUID followerId, UUID followeeId) {
        return followRepository.findById(new FollowId(followerId, followeeId)).map(Follow::getStatus);
    }

    /** True only for an ACCEPTED follow; a PENDING request does not count. */
    @Transactional(readOnly = true)
    public boolean isAcceptedFollower(UUID viewerId, UUID ownerId) {
        return followRepository.existsByIdAndStatus(new FollowId(viewerId, ownerId), FollowStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public long followerCount(UUID userId) {
        return followRepository.countByIdFolloweeIdAndStatus(userId, FollowStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public long followingCount(UUID userId) {
        return followRepository.countByIdFollowerIdAndStatus(userId, FollowStatus.ACCEPTED);
    }

    /** A private account's connections are hidden (404) from anyone who is not the owner or an accepted follower. */
    private User requireViewableAccount(UUID viewerId, String username) {
        User owner = userService.requireByUsername(username);
        boolean viewable = !owner.isPrivate()
                || owner.getId().equals(viewerId)
                || isAcceptedFollower(viewerId, owner.getId());
        if (!viewable) {
            throw new NotFoundException("User not found");
        }
        return owner;
    }

    private CursorPage<UserSummaryDto> toPage(List<User> rows, CursorPageRequest page) {
        return CursorPage.fromLookahead(rows.stream().map(userSummaries::of).toList(),
                page.limit(), UserSummaryDto::id);
    }
}
