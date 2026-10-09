package com.keepsake.user.service;

import com.keepsake.feed.service.FeedStore;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.BlockId;
import com.keepsake.user.repository.BlockRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Blocking (spec §10). A block in either direction hides each user's memories, profile, comments, and list entries
 * from the other and stops them following, tagging, or adding each other. Blocking also severs what linked them
 * (follows, pending requests, close friends, tags, album collaboration); unblocking restores none of it.
 */
@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserService userService;
    private final UserSummaries userSummaries;

    private final FeedStore feeds;

    public BlockService(
            BlockRepository blockRepository,
            UserService userService,
            UserSummaries userSummaries,
            FeedStore feeds
    ) {
        this.blockRepository = blockRepository;
        this.userService = userService;
        this.userSummaries = userSummaries;
        this.feeds = feeds;
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new InvalidRequestException("You cannot block yourself");
        }
        userService.requireById(blockedId);
        blockRepository.insertIfAbsent(blockerId, blockedId);
        blockRepository.deleteFollowsBetween(blockerId, blockedId);
        blockRepository.deleteCloseFriendsBetween(blockerId, blockedId);
        blockRepository.deleteTagsBetween(blockerId, blockedId);
        blockRepository.deleteAlbumMemoriesAddedBetween(blockerId, blockedId);
        blockRepository.deleteAlbumCollaborationBetween(blockerId, blockedId);
        feeds.invalidateAfterCommit(blockerId, blockedId);
    }

    /** Lifts the block only; follows, close friends, and tags removed by it stay removed. */
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.deleteById(new BlockId(blockerId, blockedId));
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID first, UUID second) {
        return !first.equals(second) && blockRepository.existsBetween(first, second);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummaryDto> blocked(UUID blockerId, CursorPageRequest page) {
        return CursorPage.fromLookahead(
                blockRepository.findBlocked(blockerId, page.cursorOrMax(), page.lookaheadLimit())
                        .stream().map(userSummaries::of).toList(),
                page.limit(), UserSummaryDto::id);
    }
}
