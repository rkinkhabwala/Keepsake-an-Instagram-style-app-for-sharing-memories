package com.keepsake.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.auth.service.JwtService;
import com.keepsake.common.id.UuidV7;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.feed.service.FeedFanOut;
import com.keepsake.feed.service.FeedService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prepares the k6 feed test (Phase 3 step 7), then exits. Compiled only with {@code mvn -Ploadtest} and active
 * only under the {@code loadtest} Spring profile, because it mints access tokens for accounts without their
 * passwords; per-IP login limits would otherwise stop 1,000 virtual users logging in from one machine.
 *
 * <ol>
 *   <li>Seeds, once, accounts named {@code lt_*}: regular users who each follow a sample of others and post a few
 *       memories, and celebrity accounts that everyone follows, so they sit above the fan-out threshold.</li>
 *   <li>Writes access tokens for the first {@code tokens} regular users to a JSON array file. They expire after 15
 *       minutes, like any access token.</li>
 *   <li>Reads each of those users' first feed page so the run starts with built Redis feeds.</li>
 * </ol>
 */
@Component
@Profile("loadtest")
public class LoadTestCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadTestCommand.class);
    private static final int BATCH = 1000;
    private static final List<String> VISIBILITIES = List.of(
            "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "FOLLOWERS", "FOLLOWERS",
            "FOLLOWERS", "FOLLOWERS", "CLOSE_FRIENDS");

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final FeedService feedService;
    private final FeedFanOut feedFanOut;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;
    private final LoadTestProperties properties;

    public LoadTestCommand(
            JdbcTemplate jdbc,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            FeedService feedService,
            FeedFanOut feedFanOut,
            ObjectMapper objectMapper,
            ConfigurableApplicationContext context,
            @Value("${keepsake.loadtest.users:12000}") int users,
            @Value("${keepsake.loadtest.celebrities:3}") int celebrities,
            @Value("${keepsake.loadtest.follows-per-user:40}") int followsPerUser,
            @Value("${keepsake.loadtest.memories-per-user:3}") int memoriesPerUser,
            @Value("${keepsake.loadtest.memories-per-celebrity:100}") int memoriesPerCelebrity,
            @Value("${keepsake.loadtest.tokens:1000}") int tokens,
            @Value("${keepsake.loadtest.tokens-file:/loadtest/tokens.json}") String tokensFile,
            @Value("${keepsake.loadtest.exit-when-done:true}") boolean exitWhenDone
    ) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.feedService = feedService;
        this.feedFanOut = feedFanOut;
        this.objectMapper = objectMapper;
        this.context = context;
        this.properties = new LoadTestProperties(users, celebrities, followsPerUser, memoriesPerUser,
                memoriesPerCelebrity, tokens, Path.of(tokensFile), exitWhenDone);
    }

    record LoadTestProperties(
            int users,
            int celebrities,
            int followsPerUser,
            int memoriesPerUser,
            int memoriesPerCelebrity,
            int tokens,
            Path tokensFile,
            boolean exitWhenDone
    ) {
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username LIKE 'lt\\_%'", Integer.class);
        if (existing == null || existing == 0) {
            seed();
        } else {
            log.info("Load-test accounts already exist ({}); not seeding again", existing);
        }
        // Make the celebrity accounts known now rather than at the next scheduled refresh.
        feedFanOut.refreshCelebrities();

        List<UUID> tokenUsers = jdbc.queryForList(
                "SELECT id FROM users WHERE username LIKE 'lt\\_user\\_%' ORDER BY username LIMIT ?",
                UUID.class, properties.tokens());
        List<String> tokens = tokenUsers.stream().map(jwtService::createAccessToken).toList();
        Files.createDirectories(properties.tokensFile().toAbsolutePath().getParent());
        objectMapper.writeValue(properties.tokensFile().toFile(), tokens);
        log.info("Wrote {} access tokens to {}; they expire in {}", tokens.size(), properties.tokensFile(),
                JwtService.ACCESS_TOKEN_LIFETIME);

        for (UUID userId : tokenUsers) {
            feedService.feed(userId, CursorPageRequest.parse(null, null));
        }
        log.info("Built the feeds of {} users", tokenUsers.size());

        if (properties.exitWhenDone()) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }

    private void seed() {
        Random random = new Random(20261010L);
        Instant now = Instant.now();
        String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());

        List<UUID> regular = new ArrayList<>();
        List<UUID> celebrities = new ArrayList<>();
        List<Object[]> userRows = new ArrayList<>();
        for (int i = 0; i < properties.users(); i++) {
            UUID id = UuidV7.generate();
            regular.add(id);
            userRows.add(user(id, String.format("lt_user_%05d", i), passwordHash, now));
        }
        for (int i = 0; i < properties.celebrities(); i++) {
            UUID id = UuidV7.generate();
            celebrities.add(id);
            userRows.add(user(id, "lt_celeb_" + i, passwordHash, now));
        }
        insert("""
                INSERT INTO users (id, username, email, password_hash, display_name, is_private, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?)
                """, userRows);
        log.info("Seeded {} users", userRows.size());

        List<Object[]> followRows = new ArrayList<>();
        for (UUID follower : regular) {
            Set<UUID> followees = new LinkedHashSet<>(celebrities);
            int wanted = Math.min(properties.followsPerUser(), regular.size() - 1);
            while (followees.size() < celebrities.size() + wanted) {
                UUID candidate = regular.get(random.nextInt(regular.size()));
                if (!candidate.equals(follower)) {
                    followees.add(candidate);
                }
            }
            followees.forEach(followee -> followRows.add(new Object[]{follower, followee, ts(now), ts(now)}));
        }
        insert("""
                INSERT INTO follows (follower_id, followee_id, status, created_at, updated_at)
                VALUES (?, ?, 'ACCEPTED', ?, ?)
                """, followRows);
        log.info("Seeded {} follows", followRows.size());

        // Memories are created in a shuffled author order so feeds interleave authors, oldest first, since ids
        // (UUIDv7) order the feed.
        List<UUID> authors = new ArrayList<>();
        regular.forEach(id -> {
            for (int i = 0; i < properties.memoriesPerUser(); i++) {
                authors.add(id);
            }
        });
        celebrities.forEach(id -> {
            for (int i = 0; i < properties.memoriesPerCelebrity(); i++) {
                authors.add(id);
            }
        });
        Collections.shuffle(authors, random);
        List<Object[]> memories = new ArrayList<>();
        List<Object[]> uploads = new ArrayList<>();
        List<Object[]> media = new ArrayList<>();
        for (int i = 0; i < authors.size(); i++) {
            UUID author = authors.get(i);
            UUID memoryId = UuidV7.generate();
            UUID uploadId = UuidV7.generate();
            Timestamp created = ts(now.minus(authors.size() - i, ChronoUnit.MINUTES));
            String key = "loadtest/" + uploadId;
            memories.add(new Object[]{memoryId, author, "Load test memory " + i,
                    LocalDate.of(2018 + random.nextInt(8), 1 + random.nextInt(12), 1 + random.nextInt(28)),
                    VISIBILITIES.get(random.nextInt(VISIBILITIES.size())), created, created});
            uploads.add(new Object[]{uploadId, author, key + "/original", key + "/display.webp",
                    key + "/thumb.webp", created, created});
            media.add(new Object[]{UuidV7.generate(), memoryId, uploadId, key + "/original", key + "/display.webp",
                    key + "/thumb.webp", created, created});
        }
        insert("""
                INSERT INTO memories (id, author_id, caption, occurred_on, visibility, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'READY', ?, ?)
                """, memories);
        insert("""
                INSERT INTO media_uploads (id, owner_id, content_type, size_bytes, original_key, status, display_key,
                                           thumb_key, width, height, created_at, updated_at)
                VALUES (?, ?, 'image/jpeg', 1000, ?, 'READY', ?, ?, 1080, 1080, ?, ?)
                """, uploads);
        insert("""
                INSERT INTO memory_media (id, memory_id, upload_id, position, type, original_key, display_key,
                                          thumb_key, width, height, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'IMAGE', ?, ?, ?, 1080, 1080, ?, ?)
                """, media);
        log.info("Seeded {} memories", memories.size());
    }

    private void insert(String sql, List<Object[]> rows) {
        for (int start = 0; start < rows.size(); start += BATCH) {
            jdbc.batchUpdate(sql, rows.subList(start, Math.min(rows.size(), start + BATCH)));
        }
    }

    private static Object[] user(UUID id, String username, String passwordHash, Instant now) {
        return new Object[]{id, username, username + "@loadtest.invalid", passwordHash, username, ts(now), ts(now)};
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
