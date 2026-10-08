package com.keepsake.memory.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.user.service.CloseFriendService;
import com.keepsake.user.service.FollowService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for who can see a memory (spec §5). Every feature that shows memories must go
 * through this class rather than re-deriving the rules.
 *
 * <p>A viewer V can see memory M by author A iff M is READY, not deleted, and:
 * <ul>
 *   <li>PUBLIC: A is not private, or V follows A (ACCEPTED), or V == A</li>
 *   <li>FOLLOWERS: V follows A (ACCEPTED) or V == A</li>
 *   <li>CLOSE_FRIENDS: V is in A's close friends or V == A</li>
 *   <li>ONLY_ME: V == A</li>
 * </ul>
 * A PENDING follow request grants nothing.
 */
@Component
public class MemoryAccessPolicy {

    private final FollowService followService;
    private final CloseFriendService closeFriendService;

    public MemoryAccessPolicy(FollowService followService, CloseFriendService closeFriendService) {
        this.followService = followService;
        this.closeFriendService = closeFriendService;
    }

    public boolean canView(UUID viewerId, MemoryAccessSubject memory) {
        if (memory.status() != MemoryStatus.READY || memory.deleted()) {
            return false;
        }
        if (memory.authorId().equals(viewerId)) {
            return true;
        }
        return decide(viewerId, memory, relationOf(viewerId, memory.authorId()));
    }

    /** Throws 404 rather than 403 so that a hidden memory's existence is not leaked. */
    public void requireVisible(UUID viewerId, MemoryAccessSubject memory) {
        if (!canView(viewerId, memory)) {
            throw new NotFoundException("Memory not found");
        }
    }

    /** The §5 table as a pure function of the memory and the viewer's relation to its author. */
    static boolean decide(UUID viewerId, MemoryAccessSubject memory, ViewerRelation relation) {
        if (memory.status() != MemoryStatus.READY || memory.deleted()) {
            return false;
        }
        boolean self = memory.authorId().equals(viewerId);
        return switch (memory.visibility()) {
            case PUBLIC -> !memory.authorPrivate() || relation.acceptedFollower() || self;
            case FOLLOWERS -> relation.acceptedFollower() || self;
            case CLOSE_FRIENDS -> relation.closeFriend() || self;
            case ONLY_ME -> self;
        };
    }

    private ViewerRelation relationOf(UUID viewerId, UUID authorId) {
        return new ViewerRelation(
                followService.followStatus(viewerId, authorId).orElse(null),
                closeFriendService.isCloseFriend(authorId, viewerId));
    }
}
