package com.keepsake.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.auth.service.AuthService;
import com.keepsake.engagement.service.EngagementService;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaStorage;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.memory.repository.MemoryMediaRepository;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryService;
import com.keepsake.support.IntegrationTestBase;
import com.keepsake.user.entity.FollowStatus;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import com.keepsake.user.service.CloseFriendService;
import com.keepsake.user.service.FollowService;
import com.keepsake.user.service.UserService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoSeederIntegrationTest extends IntegrationTestBase {

    private static final Set<String> USERNAMES = Set.of("ana", "ben", "cleo", "dev", "eli");

    @Autowired UserRepository userRepository;
    @Autowired AuthService authService;
    @Autowired UserService userService;
    @Autowired FollowService followService;
    @Autowired CloseFriendService closeFriendService;
    @Autowired MediaUploadRepository uploadRepository;
    @Autowired MediaStorage storage;
    @Autowired MemoryService memoryService;
    @Autowired MemoryRepository memoryRepository;
    @Autowired MemoryMediaRepository mediaRepository;
    @Autowired EngagementService engagementService;

    @Test
    void seedsUsersFollowsAndFiftyProcessedMemoriesOnce() {
        DemoSeeder seeder = new DemoSeeder(userRepository, authService, userService, followService, closeFriendService,
                uploadRepository, storage, memoryService, memoryRepository, engagementService);

        seeder.seed();

        List<User> users = userRepository.findAll().stream()
                .filter(user -> USERNAMES.contains(user.getUsername())).toList();
        assertThat(users).hasSize(5);
        User ana = byName(users, "ana");
        User cleo = byName(users, "cleo");
        assertThat(cleo.isPrivate()).isTrue();
        assertThat(followService.followStatus(ana.getId(), cleo.getId())).contains(FollowStatus.PENDING);
        assertThat(followService.followStatus(byName(users, "ben").getId(), cleo.getId())).contains(FollowStatus.ACCEPTED);

        Set<java.util.UUID> authorIds = users.stream().map(User::getId).collect(Collectors.toSet());
        List<Memory> memories = memoryRepository.findAll().stream()
                .filter(memory -> authorIds.contains(memory.getAuthorId())).toList();
        assertThat(memories).hasSize(50);
        assertThat(memories).allSatisfy(memory -> assertThat(memory.getStatus()).isEqualTo(MemoryStatus.READY));
        assertThat(memories).extracting(Memory::getVisibility).contains(Visibility.values());
        assertThat(mediaRepository.findByMemoryIdInOrderByPosition(memories.stream().map(Memory::getId).toList()))
                .allSatisfy(media -> assertThat(media.isReady()).isTrue());
        assertThat(memories.stream().mapToInt(Memory::getLikeCount).sum()).isPositive();

        // A second run (e.g. a restarted container) leaves the data alone.
        seeder.seed();
        assertThat(memoryRepository.findAll().stream().filter(memory -> authorIds.contains(memory.getAuthorId())))
                .hasSize(50);
    }

    private static User byName(List<User> users, String username) {
        return users.stream().filter(user -> user.getUsername().equals(username)).findFirst().orElseThrow();
    }
}
