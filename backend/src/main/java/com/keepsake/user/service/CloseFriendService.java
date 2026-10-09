package com.keepsake.user.service;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.CloseFriendId;
import com.keepsake.user.repository.CloseFriendRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseFriendService {

    private final CloseFriendRepository closeFriendRepository;
    private final UserService userService;
    private final UserSummaries userSummaries;

    public CloseFriendService(
            CloseFriendRepository closeFriendRepository,
            UserService userService,
            UserSummaries userSummaries
    ) {
        this.closeFriendRepository = closeFriendRepository;
        this.userService = userService;
        this.userSummaries = userSummaries;
    }

    @Transactional
    public void add(UUID ownerId, UUID friendId) {
        if (ownerId.equals(friendId)) {
            throw new InvalidRequestException("You cannot add yourself to close friends");
        }
        userService.requireById(friendId);
        closeFriendRepository.insertIfAbsent(ownerId, friendId);
    }

    @Transactional
    public void remove(UUID ownerId, UUID friendId) {
        closeFriendRepository.deleteById(new CloseFriendId(ownerId, friendId));
    }

    @Transactional(readOnly = true)
    public boolean isCloseFriend(UUID ownerId, UUID friendId) {
        return closeFriendRepository.existsById(new CloseFriendId(ownerId, friendId));
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummaryDto> list(UUID ownerId, CursorPageRequest page) {
        return CursorPage.fromLookahead(
                closeFriendRepository.findFriends(ownerId, page.cursorOrMax(), page.lookaheadLimit())
                        .stream().map(userSummaries::of).toList(),
                page.limit(), UserSummaryDto::id);
    }
}
