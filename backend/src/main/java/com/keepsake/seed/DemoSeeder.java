package com.keepsake.seed;

import com.keepsake.album.dto.AddCollaboratorRequest;
import com.keepsake.album.dto.CreateAlbumRequest;
import com.keepsake.album.entity.AlbumRole;
import com.keepsake.album.service.AlbumService;
import com.keepsake.auth.dto.SignupRequest;
import com.keepsake.auth.service.AuthService;
import com.keepsake.engagement.dto.CreateCommentRequest;
import com.keepsake.engagement.service.EngagementService;
import com.keepsake.media.entity.MediaUpload;
import com.keepsake.media.repository.MediaUploadRepository;
import com.keepsake.media.service.MediaStorage;
import com.keepsake.memory.dto.CreateMemoryRequest;
import com.keepsake.memory.dto.LocationDto;
import com.keepsake.memory.entity.MemoryStatus;
import com.keepsake.memory.entity.Visibility;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryService;
import com.keepsake.user.dto.Relationship;
import com.keepsake.user.dto.UpdateProfileRequest;
import com.keepsake.user.repository.UserRepository;
import com.keepsake.user.service.BlockService;
import com.keepsake.user.service.CloseFriendService;
import com.keepsake.user.service.FollowService;
import com.keepsake.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Demo data for local runs: 5 users (one private, with a pending follow request), follows, close friends, and 50
 * memories with generated photos, plus likes and comments. Enabled only with {@code keepsake.seed.enabled=true};
 * runs once, in the background, through the real services so seeded data goes through the same rules and media
 * pipeline as user data. Skips itself when the demo users already exist.
 */
@Component
@ConditionalOnProperty(name = "keepsake.seed.enabled", havingValue = "true")
public class DemoSeeder {

    public static final String PASSWORD = "keepsake-demo";
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final int MEMORIES_PER_USER = 10;

    private static final List<String[]> USERS = List.of(
            new String[] {"ana", "Ana Lima", "Collecting small moments."},
            new String[] {"ben", "Ben Chen", "Weekend hikes and long lunches."},
            new String[] {"cleo", "Cleo Morgan", "Private account: friends only."},
            new String[] {"dev", "Dev Okafor", "Film photos, mostly."},
            new String[] {"eli", "Eli Novak", "Trying to remember more."});

    private static final String[] CAPTIONS = {
            "The light was unreal tonight #goldenhour",
            "First swim of the summer #summer",
            "Sunday market haul with @%s",
            "We walked until the road ran out #travel #hiking",
            "Grandma's recipe, finally right #family",
            "Caught the last train home",
            "Nobody else on the trail",
            "Birthday dinner, too much cake #birthday",
            "Rainy afternoon, good book",
            "Found this spot by accident",
    };

    /** Places with coordinates, so seeded memories show up on the map; null means no location. */
    private static final LocationDto[] PLACES = {
            new LocationDto("Lisbon", 38.7223, -9.1393),
            new LocationDto("Big Sur", 36.2704, -121.8081),
            new LocationDto("Kyoto", 35.0116, 135.7681),
            new LocationDto("Lake Bled", 46.3683, 14.1146),
            new LocationDto("Cape Town", -33.9249, 18.4241),
            new LocationDto("Edinburgh", 55.9533, -3.1883),
            new LocationDto("Oaxaca", 17.0732, -96.7266),
            new LocationDto("Banff", 51.1784, -115.5708),
            null,
            null};

    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserService userService;
    private final FollowService followService;
    private final CloseFriendService closeFriendService;
    private final MediaUploadRepository uploadRepository;
    private final MediaStorage storage;
    private final MemoryService memoryService;
    private final MemoryRepository memoryRepository;
    private final EngagementService engagementService;
    private final AlbumService albumService;
    private final BlockService blockService;

    public DemoSeeder(
            UserRepository userRepository,
            AuthService authService,
            UserService userService,
            FollowService followService,
            CloseFriendService closeFriendService,
            MediaUploadRepository uploadRepository,
            MediaStorage storage,
            MemoryService memoryService,
            MemoryRepository memoryRepository,
            EngagementService engagementService,
            AlbumService albumService,
            BlockService blockService
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.userService = userService;
        this.followService = followService;
        this.closeFriendService = closeFriendService;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
        this.engagementService = engagementService;
        this.albumService = albumService;
        this.blockService = blockService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedInBackground() {
        Thread.ofVirtual().name("demo-seeder").start(() -> {
            try {
                seed();
            } catch (RuntimeException exception) {
                log.error("Demo seed failed", exception);
            }
        });
    }

    void seed() {
        if (userRepository.existsByUsername("ana")) {
            log.info("Demo seed skipped: demo users already exist");
            return;
        }
        Instant started = Instant.now();
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (String[] user : USERS) {
            UUID id = authService.signup(new SignupRequest(user[0], user[0] + "@keepsake.test", PASSWORD, user[1]))
                    .response().user().id();
            userService.updateMe(id, new UpdateProfileRequest(null, user[2], user[0].equals("cleo"), null));
            ids.put(user[0], id);
        }

        follow(ids, "ana", "ben");
        follow(ids, "ben", "ana");
        follow(ids, "ben", "cleo");
        follow(ids, "dev", "ana");
        follow(ids, "eli", "ana");
        follow(ids, "ana", "dev");
        follow(ids, "eli", "ben");
        follow(ids, "cleo", "ana");
        // ana's request to follow the private account cleo stays PENDING.
        followService.follow(ids.get("ana"), "cleo");
        closeFriendService.add(ids.get("ana"), ids.get("ben"));
        closeFriendService.add(ids.get("ben"), ids.get("ana"));

        Random random = new Random(42);
        List<UUID> memories = new ArrayList<>();
        List<String> usernames = new ArrayList<>(ids.keySet());
        Map<String, List<UUID>> byAuthor = new LinkedHashMap<>();
        for (int round = 0; round < MEMORIES_PER_USER; round++) {
            for (String username : usernames) {
                UUID memoryId = round == 0 && username.equals("ana")
                        // A leap-day memory, so On This Day's Feb 29 rule has something to show.
                        ? createMemory(ids, username, "Leap day picnic #leapday", LocalDate.of(2024, 2, 29),
                                PLACES[0], Visibility.PUBLIC, List.of(), random)
                        : createMemory(ids, username, usernames, round, random);
                memories.add(memoryId);
                byAuthor.computeIfAbsent(username, name -> new ArrayList<>()).add(memoryId);
            }
        }

        awaitProcessing(memories);
        addEngagement(ids, memories, random);
        addSharedAlbum(ids, byAuthor);
        // A block between two users who otherwise have nothing to do with each other.
        blockService.block(ids.get("eli"), ids.get("dev"));
        log.info("Demo seed complete: {} users, {} memories in {}s (password for every user: {})",
                ids.size(), memories.size(), Duration.between(started, Instant.now()).toSeconds(), PASSWORD);
    }

    /** Follows, and has a private followee approve the request, leaving an ACCEPTED follow either way. */
    private void follow(Map<String, UUID> ids, String follower, String followee) {
        if (followService.follow(ids.get(follower), followee) == Relationship.REQUESTED) {
            followService.acceptRequest(ids.get(followee), ids.get(follower));
        }
    }

    private UUID createMemory(Map<String, UUID> ids, String username, List<String> usernames, int round, Random random) {
        String friend = usernames.get((usernames.indexOf(username) + 1 + random.nextInt(usernames.size() - 1))
                % usernames.size());
        String caption = CAPTIONS[(round + usernames.indexOf(username)) % CAPTIONS.length].formatted(friend);
        LocationDto place = PLACES[random.nextInt(PLACES.length)];
        Visibility visibility = switch (round % 10) {
            case 3 -> Visibility.FOLLOWERS;
            case 6 -> Visibility.CLOSE_FRIENDS;
            case 9 -> Visibility.ONLY_ME;
            default -> Visibility.PUBLIC;
        };
        LocalDate occurredOn = LocalDate.now().minusDays(round * 37L + random.nextInt(30)).minusYears(round % 4);
        return createMemory(ids, username, caption, occurredOn, place, visibility,
                caption.contains("@") ? List.of(friend) : List.of(), random);
    }

    private UUID createMemory(
            Map<String, UUID> ids,
            String username,
            String caption,
            LocalDate occurredOn,
            LocationDto place,
            Visibility visibility,
            List<String> tagged,
            Random random
    ) {
        UUID authorId = ids.get(username);
        int photos = 1 + random.nextInt(3);
        List<UUID> uploadIds = new ArrayList<>();
        for (int photo = 0; photo < photos; photo++) {
            boolean portrait = random.nextInt(3) == 0;
            byte[] bytes = SampleImages.landscape(random.nextLong(), portrait ? 1200 : 1600, portrait ? 1500 : 1200);
            MediaUpload upload = uploadRepository.save(new MediaUpload(authorId, "image/jpeg", bytes.length));
            storage.write(upload.getOriginalKey(), bytes, "image/jpeg");
            uploadIds.add(upload.getId());
        }
        return memoryService.create(authorId,
                new CreateMemoryRequest(caption, occurredOn, place, visibility, tagged, uploadIds)).id();
    }

    /** ana's followers-only album with ben as an editor; each adds their own memories of mixed visibility. */
    private void addSharedAlbum(Map<String, UUID> ids, Map<String, List<UUID>> byAuthor) {
        UUID ana = ids.get("ana");
        UUID ben = ids.get("ben");
        UUID album = albumService.create(ana, new CreateAlbumRequest(
                "Trips with Ben", "Places we went together.", Visibility.FOLLOWERS)).id();
        albumService.addCollaborator(ana, album, new AddCollaboratorRequest(ben, AlbumRole.EDITOR));
        albumService.addMemories(ana, album, byAuthor.get("ana").subList(0, 4));
        albumService.addMemories(ben, album, byAuthor.get("ben").subList(0, 3));
    }

    private void awaitProcessing(List<UUID> memories) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (Instant.now().isBefore(deadline)) {
            long pending = memoryRepository.findAllById(memories).stream()
                    .filter(memory -> memory.getStatus() == MemoryStatus.PROCESSING)
                    .count();
            if (pending == 0) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Demo seed: some memories were still processing after 5 minutes");
    }

    /** Likes and comments go through the same visibility checks as users; ones a viewer can't see are skipped. */
    private void addEngagement(Map<String, UUID> ids, List<UUID> memories, Random random) {
        String[] comments = {"Love this!", "Take me back", "So good", "When was this?!", "Beautiful"};
        for (UUID memoryId : memories) {
            for (UUID viewer : ids.values()) {
                if (random.nextInt(3) == 0) {
                    try {
                        engagementService.like(viewer, memoryId);
                        if (random.nextInt(3) == 0) {
                            engagementService.comment(viewer, memoryId,
                                    new CreateCommentRequest(comments[random.nextInt(comments.length)], null));
                        }
                    } catch (RuntimeException hidden) {
                        // The viewer cannot see this memory, exactly as the API would refuse.
                    }
                }
            }
        }
    }
}
