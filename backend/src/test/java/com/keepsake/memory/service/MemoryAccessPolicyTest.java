package com.keepsake.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.user.entity.BlockId;
import com.keepsake.user.entity.FollowStatus;
import com.keepsake.user.repository.BlockRepository;
import com.keepsake.user.service.CloseFriendService;
import com.keepsake.user.service.FollowService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class MemoryAccessPolicyTest {

    private static final UUID AUTHOR = UUID.fromString("01900000-0000-7000-8000-00000000000a");
    private static final UUID VIEWER = UUID.fromString("01900000-0000-7000-8000-00000000000b");

    private static final boolean PUBLIC_ACCOUNT = false;
    private static final boolean PRIVATE_ACCOUNT = true;

    private static final ViewerRelation STRANGER = ViewerRelation.NONE;
    private static final ViewerRelation PENDING = new ViewerRelation(FollowStatus.PENDING, false);
    private static final ViewerRelation FOLLOWER = new ViewerRelation(FollowStatus.ACCEPTED, false);
    private static final ViewerRelation CLOSE_FRIEND_ONLY = new ViewerRelation(null, true);
    private static final ViewerRelation PENDING_CLOSE_FRIEND = new ViewerRelation(FollowStatus.PENDING, true);
    private static final ViewerRelation FOLLOWER_AND_CLOSE_FRIEND = new ViewerRelation(FollowStatus.ACCEPTED, true);

    /** Every row of spec §5 for a non-author viewer, on public and private accounts. */
    static Stream<Arguments> visibilityTable() {
        return Stream.of(
                // PUBLIC: A is not private, or V follows A (ACCEPTED)
                Arguments.of(Visibility.PUBLIC, PUBLIC_ACCOUNT, STRANGER, true),
                Arguments.of(Visibility.PUBLIC, PUBLIC_ACCOUNT, PENDING, true),
                Arguments.of(Visibility.PUBLIC, PUBLIC_ACCOUNT, FOLLOWER, true),
                Arguments.of(Visibility.PUBLIC, PRIVATE_ACCOUNT, STRANGER, false),
                Arguments.of(Visibility.PUBLIC, PRIVATE_ACCOUNT, PENDING, false),
                Arguments.of(Visibility.PUBLIC, PRIVATE_ACCOUNT, CLOSE_FRIEND_ONLY, false),
                Arguments.of(Visibility.PUBLIC, PRIVATE_ACCOUNT, FOLLOWER, true),
                // FOLLOWERS: V follows A (ACCEPTED), whether or not A is private
                Arguments.of(Visibility.FOLLOWERS, PUBLIC_ACCOUNT, STRANGER, false),
                Arguments.of(Visibility.FOLLOWERS, PUBLIC_ACCOUNT, PENDING, false),
                Arguments.of(Visibility.FOLLOWERS, PUBLIC_ACCOUNT, CLOSE_FRIEND_ONLY, false),
                Arguments.of(Visibility.FOLLOWERS, PUBLIC_ACCOUNT, FOLLOWER, true),
                Arguments.of(Visibility.FOLLOWERS, PRIVATE_ACCOUNT, STRANGER, false),
                Arguments.of(Visibility.FOLLOWERS, PRIVATE_ACCOUNT, PENDING, false),
                Arguments.of(Visibility.FOLLOWERS, PRIVATE_ACCOUNT, FOLLOWER, true),
                // CLOSE_FRIENDS: V is in A's close friends; following alone is not enough
                Arguments.of(Visibility.CLOSE_FRIENDS, PUBLIC_ACCOUNT, STRANGER, false),
                Arguments.of(Visibility.CLOSE_FRIENDS, PUBLIC_ACCOUNT, FOLLOWER, false),
                Arguments.of(Visibility.CLOSE_FRIENDS, PUBLIC_ACCOUNT, CLOSE_FRIEND_ONLY, true),
                Arguments.of(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, PENDING, false),
                Arguments.of(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, FOLLOWER, false),
                Arguments.of(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, CLOSE_FRIEND_ONLY, true),
                Arguments.of(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, PENDING_CLOSE_FRIEND, true),
                Arguments.of(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, FOLLOWER_AND_CLOSE_FRIEND, true),
                // ONLY_ME: nobody but the author, however close
                Arguments.of(Visibility.ONLY_ME, PUBLIC_ACCOUNT, STRANGER, false),
                Arguments.of(Visibility.ONLY_ME, PUBLIC_ACCOUNT, FOLLOWER_AND_CLOSE_FRIEND, false),
                Arguments.of(Visibility.ONLY_ME, PRIVATE_ACCOUNT, FOLLOWER_AND_CLOSE_FRIEND, false));
    }

    @ParameterizedTest(name = "{0} on private={1} for {2} -> {3}")
    @MethodSource("visibilityTable")
    void appliesVisibilityTableToOtherViewers(
            Visibility visibility,
            boolean authorPrivate,
            ViewerRelation relation,
            boolean expected
    ) {
        MemoryAccessSubject memory = memory(visibility, authorPrivate, MemoryStatus.READY, false);

        assertThat(MemoryAccessPolicy.decide(VIEWER, memory, relation)).isEqualTo(expected);
    }

    /**
     * Block rows of the visibility table: a block in either direction hides every memory, whatever its
     * visibility, the account type, or the follow and close-friend state.
     */
    static Stream<Arguments> blockedRows() {
        List<ViewerRelation> relations = List.of(
                STRANGER, PENDING, FOLLOWER, CLOSE_FRIEND_ONLY, PENDING_CLOSE_FRIEND, FOLLOWER_AND_CLOSE_FRIEND);
        Stream.Builder<Arguments> rows = Stream.builder();
        for (Visibility visibility : Visibility.values()) {
            for (boolean authorPrivate : new boolean[] {PUBLIC_ACCOUNT, PRIVATE_ACCOUNT}) {
                for (ViewerRelation relation : relations) {
                    rows.add(Arguments.of(visibility, authorPrivate, "viewer blocked author", new ViewerRelation(
                            relation.followStatus(), relation.closeFriend(), true, false)));
                    rows.add(Arguments.of(visibility, authorPrivate, "author blocked viewer", new ViewerRelation(
                            relation.followStatus(), relation.closeFriend(), false, true)));
                }
            }
        }
        return rows.build();
    }

    @ParameterizedTest(name = "{0} on private={1}: {2} with {3} -> hidden")
    @MethodSource("blockedRows")
    void blockInEitherDirectionHidesEveryMemory(
            Visibility visibility,
            boolean authorPrivate,
            String direction,
            ViewerRelation relation
    ) {
        MemoryAccessSubject memory = memory(visibility, authorPrivate, MemoryStatus.READY, false);

        assertThat(MemoryAccessPolicy.decide(VIEWER, memory, relation)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Visibility.class)
    void blocksNeverHideAMemoryFromItsAuthor(Visibility visibility) {
        MemoryAccessSubject memory = memory(visibility, PRIVATE_ACCOUNT, MemoryStatus.READY, false);

        assertThat(MemoryAccessPolicy.decide(AUTHOR, memory, new ViewerRelation(null, false, true, true))).isTrue();
    }

    @Test
    void canViewLoadsBothBlockDirections() {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        when(followService.followStatus(VIEWER, AUTHOR)).thenReturn(Optional.of(FollowStatus.ACCEPTED));
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, blockRepository);
        MemoryAccessSubject publicMemory = memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.READY, false);

        assertThat(policy.canView(VIEWER, publicMemory)).isTrue();
        when(blockRepository.existsById(new BlockId(AUTHOR, VIEWER))).thenReturn(true);
        assertThat(policy.canView(VIEWER, publicMemory)).isFalse();
        when(blockRepository.existsById(new BlockId(AUTHOR, VIEWER))).thenReturn(false);
        when(blockRepository.existsById(new BlockId(VIEWER, AUTHOR))).thenReturn(true);
        assertThat(policy.canView(VIEWER, publicMemory)).isFalse();
        assertThat(policy.canOpen(AUTHOR, publicMemory)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Visibility.class)
    void authorAlwaysSeesOwnReadyMemoryEvenOnPrivateAccount(Visibility visibility) {
        MemoryAccessSubject memory = memory(visibility, PRIVATE_ACCOUNT, MemoryStatus.READY, false);

        assertThat(MemoryAccessPolicy.decide(AUTHOR, memory, ViewerRelation.NONE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MemoryStatus.class, names = {"PROCESSING", "FAILED"})
    void memoriesThatAreNotReadyAreHiddenFromEveryone(MemoryStatus status) {
        MemoryAccessSubject memory = memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, status, false);

        assertThat(MemoryAccessPolicy.decide(VIEWER, memory, FOLLOWER_AND_CLOSE_FRIEND)).isFalse();
        assertThat(MemoryAccessPolicy.decide(AUTHOR, memory, ViewerRelation.NONE)).isFalse();
    }

    @Test
    void deletedMemoriesAreHiddenFromEveryone() {
        MemoryAccessSubject memory = memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.READY, true);

        assertThat(MemoryAccessPolicy.decide(VIEWER, memory, FOLLOWER_AND_CLOSE_FRIEND)).isFalse();
        assertThat(MemoryAccessPolicy.decide(AUTHOR, memory, ViewerRelation.NONE)).isFalse();
    }

    @Test
    void canViewLoadsViewerRelationFromFollowAndCloseFriendServices() {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        when(followService.followStatus(VIEWER, AUTHOR)).thenReturn(Optional.of(FollowStatus.PENDING));
        when(closeFriendService.isCloseFriend(AUTHOR, VIEWER)).thenReturn(true);
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, mock(BlockRepository.class));

        assertThat(policy.canView(VIEWER, memory(Visibility.CLOSE_FRIENDS, PRIVATE_ACCOUNT, MemoryStatus.READY, false)))
                .isTrue();
        assertThat(policy.canView(VIEWER, memory(Visibility.FOLLOWERS, PRIVATE_ACCOUNT, MemoryStatus.READY, false)))
                .isFalse();
    }

    @Test
    void canViewSkipsRelationLookupsForAuthorAndUnreadyMemories() {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, mock(BlockRepository.class));

        assertThat(policy.canView(AUTHOR, memory(Visibility.ONLY_ME, PRIVATE_ACCOUNT, MemoryStatus.READY, false)))
                .isTrue();
        assertThat(policy.canView(VIEWER, memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.FAILED, false)))
                .isFalse();
        verifyNoInteractions(followService, closeFriendService);
    }

    @Test
    void requireVisibleReportsHiddenMemoriesAsNotFound() {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        when(followService.followStatus(VIEWER, AUTHOR)).thenReturn(Optional.empty());
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, mock(BlockRepository.class));

        assertThatThrownBy(() -> policy.requireVisible(
                VIEWER, memory(Visibility.FOLLOWERS, PUBLIC_ACCOUNT, MemoryStatus.READY, false)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Memory not found");
    }

    @ParameterizedTest
    @EnumSource(MemoryStatus.class)
    void authorCanOpenOwnMemoryInAnyStatusUnlessDeleted(MemoryStatus status) {
        MemoryAccessPolicy policy = new MemoryAccessPolicy(mock(FollowService.class), mock(CloseFriendService.class), mock(BlockRepository.class));

        assertThat(policy.canOpen(AUTHOR, memory(Visibility.ONLY_ME, PRIVATE_ACCOUNT, status, false))).isTrue();
        assertThat(policy.canOpen(AUTHOR, memory(Visibility.ONLY_ME, PRIVATE_ACCOUNT, status, true))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MemoryStatus.class, names = {"PROCESSING", "FAILED"})
    void othersCannotOpenUnfinishedMemoriesEvenWithFullAccess(MemoryStatus status) {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        when(followService.followStatus(VIEWER, AUTHOR)).thenReturn(Optional.of(FollowStatus.ACCEPTED));
        when(closeFriendService.isCloseFriend(AUTHOR, VIEWER)).thenReturn(true);
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, mock(BlockRepository.class));

        assertThatThrownBy(() -> policy.requireOpenable(
                VIEWER, memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, status, false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void openingReadyMemoryFollowsTheVisibilityTable() {
        FollowService followService = mock(FollowService.class);
        CloseFriendService closeFriendService = mock(CloseFriendService.class);
        when(followService.followStatus(VIEWER, AUTHOR)).thenReturn(Optional.of(FollowStatus.PENDING));
        MemoryAccessPolicy policy = new MemoryAccessPolicy(followService, closeFriendService, mock(BlockRepository.class));

        assertThat(policy.canOpen(VIEWER, memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.READY, false)))
                .isTrue();
        assertThat(policy.canOpen(VIEWER, memory(Visibility.PUBLIC, PRIVATE_ACCOUNT, MemoryStatus.READY, false)))
                .isFalse();
    }

    @Test
    void onlyTheAuthorPassesRequireAuthor() {
        MemoryAccessPolicy policy = new MemoryAccessPolicy(mock(FollowService.class), mock(CloseFriendService.class), mock(BlockRepository.class));
        MemoryAccessSubject readyPublic = memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.READY, false);

        policy.requireAuthor(AUTHOR, readyPublic);
        assertThatThrownBy(() -> policy.requireAuthor(VIEWER, readyPublic)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> policy.requireAuthor(
                AUTHOR, memory(Visibility.PUBLIC, PUBLIC_ACCOUNT, MemoryStatus.READY, true)))
                .isInstanceOf(NotFoundException.class);
    }

    private static MemoryAccessSubject memory(
            Visibility visibility,
            boolean authorPrivate,
            MemoryStatus status,
            boolean deleted
    ) {
        return new MemoryAccessSubject(AUTHOR, authorPrivate, visibility, status, deleted);
    }
}
