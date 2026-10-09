package com.keepsake.memory.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.user.entity.BlockId;
import com.keepsake.user.repository.BlockRepository;
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
 * A PENDING follow request grants nothing. A block in either direction between V and A hides every memory of A from
 * V (and of V from A), whatever its visibility or the follow and close-friend state.
 *
 * <p>Lists (feed, grid, tagged) only ever show memories passing {@link #canView}. Opening a single memory
 * ({@link #canOpen}) additionally lets the author see their own PROCESSING or FAILED memory, so they can follow
 * its progress and retry it.
 */
@Component
public class MemoryAccessPolicy {

    /**
     * The same rules as {@link #canView} expressed as a native SQL predicate, for list queries that must filter in
     * the database. Requires the memory aliased {@code m}, its author (users) aliased {@code a}, and a
     * {@code :viewerId} parameter. {@code MemoryAccessPolicySqlParityTest} checks it agrees with {@link #decide}
     * for every combination of inputs; change both together.
     */
    public static final String VISIBLE_TO_VIEWER_SQL = """
            (m.status = 'READY' AND m.deleted_at IS NULL AND (
                m.author_id = :viewerId
                OR NOT EXISTS (
                    SELECT 1 FROM blocks vb
                    WHERE (vb.blocker_id = :viewerId AND vb.blocked_id = m.author_id)
                       OR (vb.blocker_id = m.author_id AND vb.blocked_id = :viewerId))
            ) AND (
                m.author_id = :viewerId
                OR (m.visibility = 'PUBLIC' AND (a.is_private = FALSE OR EXISTS (
                    SELECT 1 FROM follows vf WHERE vf.follower_id = :viewerId
                        AND vf.followee_id = m.author_id AND vf.status = 'ACCEPTED')))
                OR (m.visibility = 'FOLLOWERS' AND EXISTS (
                    SELECT 1 FROM follows vf WHERE vf.follower_id = :viewerId
                        AND vf.followee_id = m.author_id AND vf.status = 'ACCEPTED'))
                OR (m.visibility = 'CLOSE_FRIENDS' AND EXISTS (
                    SELECT 1 FROM close_friends vc WHERE vc.owner_id = m.author_id AND vc.friend_id = :viewerId))))
            """;

    private final FollowService followService;
    private final CloseFriendService closeFriendService;
    private final BlockRepository blockRepository;

    public MemoryAccessPolicy(
            FollowService followService,
            CloseFriendService closeFriendService,
            BlockRepository blockRepository
    ) {
        this.followService = followService;
        this.closeFriendService = closeFriendService;
        this.blockRepository = blockRepository;
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

    /** Detail access: everything {@link #canView} allows, plus the author's own unfinished memories. */
    public boolean canOpen(UUID viewerId, MemoryAccessSubject memory) {
        if (memory.deleted()) {
            return false;
        }
        return memory.authorId().equals(viewerId) || canView(viewerId, memory);
    }

    /** Throws 404 rather than 403 so that a hidden memory's existence is not leaked. */
    public void requireOpenable(UUID viewerId, MemoryAccessSubject memory) {
        if (!canOpen(viewerId, memory)) {
            throw new NotFoundException("Memory not found");
        }
    }

    /** Only the author may change or delete a memory; anyone else gets the same 404 as for a hidden memory. */
    public void requireAuthor(UUID viewerId, MemoryAccessSubject memory) {
        if (memory.deleted() || !memory.authorId().equals(viewerId)) {
            throw new NotFoundException("Memory not found");
        }
    }

    /** The §5 table as a pure function of the memory and the viewer's relation to its author. */
    static boolean decide(UUID viewerId, MemoryAccessSubject memory, ViewerRelation relation) {
        if (memory.status() != MemoryStatus.READY || memory.deleted()) {
            return false;
        }
        boolean self = memory.authorId().equals(viewerId);
        if (!self && relation.blocked()) {
            return false;
        }
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
                closeFriendService.isCloseFriend(authorId, viewerId),
                blockRepository.existsById(new BlockId(viewerId, authorId)),
                blockRepository.existsById(new BlockId(authorId, viewerId)));
    }
}
