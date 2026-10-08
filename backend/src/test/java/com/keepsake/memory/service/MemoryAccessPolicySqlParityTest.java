package com.keepsake.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.user.entity.FollowStatus;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.CloseFriendRepository;
import com.keepsake.user.repository.FollowRepository;
import com.keepsake.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves {@link MemoryAccessPolicy#VISIBLE_TO_VIEWER_SQL} (used by every list query) makes the same decision as
 * {@link MemoryAccessPolicy#decide} for every combination of author privacy, viewer relation, visibility, status,
 * and deletion, including the author viewing their own memories.
 */
@Transactional
class MemoryAccessPolicySqlParityTest extends IntegrationTestBase {

    private static final List<ViewerRelation> RELATIONS = List.of(
            ViewerRelation.NONE,
            new ViewerRelation(FollowStatus.PENDING, false),
            new ViewerRelation(FollowStatus.ACCEPTED, false),
            new ViewerRelation(null, true),
            new ViewerRelation(FollowStatus.PENDING, true),
            new ViewerRelation(FollowStatus.ACCEPTED, true));

    @Autowired
    UserRepository userRepository;

    @Autowired
    FollowRepository followRepository;

    @Autowired
    CloseFriendRepository closeFriendRepository;

    @Autowired
    MemoryRepository memoryRepository;

    @Test
    void sqlPredicateMatchesPolicyForEveryCombination() {
        int checked = 0;
        for (boolean authorPrivate : new boolean[] {false, true}) {
            User author = user("parity_author_" + authorPrivate, authorPrivate);
            List<Memory> memories = memoriesCoveringEveryVisibilityStatusAndDeletion(author);

            for (Memory memory : memories) {
                MemoryAccessSubject subject = new MemoryAccessSubject(author.getId(), authorPrivate,
                        memory.getVisibility(), memory.getStatus(), memory.isDeleted());
                assertThat(memoryRepository.isVisibleTo(memory.getId(), author.getId()))
                        .as("author on %s", describe(memory))
                        .isEqualTo(MemoryAccessPolicy.decide(author.getId(), subject, ViewerRelation.NONE));
                checked++;
            }

            for (int index = 0; index < RELATIONS.size(); index++) {
                ViewerRelation relation = RELATIONS.get(index);
                User viewer = user("parity_viewer_" + authorPrivate + "_" + index, false);
                relate(viewer, author, relation);
                for (Memory memory : memories) {
                    MemoryAccessSubject subject = new MemoryAccessSubject(author.getId(), authorPrivate,
                            memory.getVisibility(), memory.getStatus(), memory.isDeleted());
                    assertThat(memoryRepository.isVisibleTo(memory.getId(), viewer.getId()))
                            .as("%s on private=%s %s", relation, authorPrivate, describe(memory))
                            .isEqualTo(MemoryAccessPolicy.decide(viewer.getId(), subject, relation));
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(2 * 7 * 4 * 3 * 2);
    }

    private List<Memory> memoriesCoveringEveryVisibilityStatusAndDeletion(User author) {
        List<Memory> memories = new ArrayList<>();
        for (Visibility visibility : Visibility.values()) {
            for (MemoryStatus status : MemoryStatus.values()) {
                for (boolean deleted : new boolean[] {false, true}) {
                    Memory memory = new Memory(author.getId(), LocalDate.of(2024, 1, 1), visibility);
                    memory.setStatus(status);
                    if (deleted) {
                        memory.softDelete(Instant.now());
                    }
                    memories.add(memoryRepository.save(memory));
                }
            }
        }
        memoryRepository.flush();
        return memories;
    }

    private void relate(User viewer, User author, ViewerRelation relation) {
        if (relation.followStatus() != null) {
            followRepository.insertIfAbsent(viewer.getId(), author.getId(), relation.followStatus().name());
        }
        if (relation.closeFriend()) {
            closeFriendRepository.insertIfAbsent(author.getId(), viewer.getId());
        }
    }

    private User user(String username, boolean isPrivate) {
        User user = new User(username, username + "@example.test", "not-a-real-hash", username);
        user.updateProfile(username, null, isPrivate);
        return userRepository.saveAndFlush(user);
    }

    private static String describe(Memory memory) {
        return memory.getVisibility() + "/" + memory.getStatus() + (memory.isDeleted() ? "/deleted" : "");
    }
}
