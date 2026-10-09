# Keepsake Phase 3 implementation plan

Phases 1 and 2 are complete and merged into `main` (their plans are in git history). This plan covers spec §12 Phase 3 only:
- Kafka for media and fan-out events;
- a fan-out-on-write feed with the celebrity hybrid;
- Redis counters with periodic flush;
- video via FFmpeg;
- notifications over WebSocket;
- Prometheus and Grafana;
- a k6 load test.

Same working rules as before:
- work on a `phase-3` branch, one feature at a time, each with its migration and tests;
- `mvn verify`, `npm test`, and the Playwright suite pass before moving on;
- small scoped commits;
- ask before adding anything outside spec §2 (the open questions at the end list these).

## Phase 3 acceptance criteria (spec §12)

1. A k6 test with 1,000 virtual users scrolling the feed sustains p95 < 300 ms on a dev laptop.
2. Unfollowing someone removes their memories from your feed on the next request, even if already fanned out.
3. Notifications arrive in the open browser within 2 s of the triggering action.

## Build order

1. **Observability first, so later steps can be measured.** This also closes a Phase 1 gap: spec §11 asks for `/actuator/prometheus`, but the Prometheus registry was never added, so the endpoint doesn't exist today.
   - Add the registry and permit the endpoint (bound to localhost).
   - Add timers for `/api/feed`, media processing time, and queue lag.
   - Add Prometheus and Grafana to Compose, with a provisioned dashboard for feed p95, processing time, and queue lag (§11).
2. **Kafka and a transactional outbox.**
   - Add a single-node Kafka (KRaft) to Compose.
   - Events are written to an `outbox_events` table in the same transaction as the change. A relay publishes them to Kafka, then marks them sent. Nothing is lost if the app stops between commit and publish, which replaces today's after-commit `@Async` handoff.
   - Topics:
     - `media.process`, keyed by memory, replacing the `@Async` processor with a consumer group;
     - `memory.events` (READY, deleted, visibility changed);
     - `social.events` (follow, unfollow, block, like, comment, tag).
   - A retry topic and a dead-letter topic handle poison messages. The existing stuck-PROCESSING sweep stays as a safety net.
3. **Video (FFmpeg, §6):**
   - accept MP4 and MOV up to 100 MB and 60 s;
   - verify with `ffprobe` (container, duration, real video stream);
   - make a poster frame (EXIF-free, with blurhash) and a 720p H.264 transcode with metadata stripped;
   - `memory_media.type = VIDEO` with `duration_ms`.
   FFmpeg is installed in the backend image, as `heif-dec` was. The client uploads and plays videos (`<video>` with poster, muted autoplay in the feed). Test fixtures are generated with FFmpeg during the test run, not committed.
4. **Redis counters with periodic flush (§7).**
   - Likes and comments increment Redis counters, and a scheduled job flushes deltas to `like_count` and `comment_count` every few seconds.
   - Reads use Redis, falling back to the database columns on a miss.
   - The `likes` and `comments` rows stay the source of truth, so a lost delta can be recomputed.
5. **Fan-out-on-write feed with celebrity hybrid (§7).**
   - **Write:** on READY, the memory id goes into each eligible follower's Redis sorted set `feed:{userId}` (score = time, capped at 800). Eligibility is pre-filtered by visibility: followers; close friends only for CLOSE_FRIENDS; nobody for ONLY_ME. Authors with more than 10k followers are not fanned out; their memories are merged in at read time.
   - **Read:**
     - take a page from the sorted set, plus followed celebrities' memories from the database;
     - re-check every candidate in one batch through `MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL` and the feed scope (author is the viewer, or an ACCEPTED follow exists now).
     So unfollows, close-friend removals, visibility edits, deletions, and blocks are respected even for entries already fanned out (acceptance 2).
   - **Optimizations, not needed for correctness:** unfollow and block also remove entries eagerly. Following someone backfills their recent memories.
   - **Cold start:** a missing feed set is rebuilt from the Phase 1 fan-out-on-read query. That query also stays as the test oracle.
6. **Notifications (Phase 3 §8):**
   - **Data:** a `notifications` table with types LIKE, COMMENT, FOLLOW, FOLLOW_REQUEST, TAGGED and ON_THIS_DAY, plus `GET /api/notifications` and `POST /api/notifications/read`.
   - **Flow:** notifications are produced from `social.events` and `memory.events` consumers. Recipients never get notifications about actors they're in a block with, or about memories they can't see (checked through the policy).
   - **Live delivery:** over STOMP at `GET /ws` to `/user/queue/notifications`, authenticated with the access token on CONNECT.
   - **Web:** a bell with an unread badge (§9 screen 10) and a notifications page.
   - **Testing:** a Playwright test checks a like shows up in the other user's open browser within 2 s (acceptance 3).
7. **k6 load test.**
   - A load-test seed (opt-in and separate from the demo seed) creates enough users, follows (including a few >10k-follower "celebrities"), and memories to make the feed realistic.
   - A test-only command mints access tokens to a file, because per-IP login limits stop 1,000 virtual users from logging in from one machine.
   - The k6 script (run with the `grafana/k6` image) has each virtual user scroll several feed pages with think time.
   - A threshold fails the run if p95 ≥ 300 ms (acceptance 1).
   - I'll tune the Hikari pool, Tomcat threads, and Redis pipelining only as the measurements show the need.
8. **Verification.**
   - All suites and Playwright flows pass, including the new notification flow.
   - The k6 run passes on this laptop.
   - The Grafana dashboard shows the feed p95, processing-time, and queue-lag panels.
   - A parity test checks the fan-out feed returns the same memories as the fan-out-on-read oracle across random follow, unfollow, visibility, close-friend, and block changes.

## Migrations

- `V13__outbox.sql`: `outbox_events (id, topic, key, payload JSONB, created_at, published_at)` with an index on unpublished rows.
- `V14__video_media.sql`: `memory_media.poster_key`, and upload limits by type.
- `V15__notifications.sql`: `notifications (id, recipient_id, actor_id, type, memory_id, comment_id, read_at, created_at, updated_at)` with indexes on `(recipient_id, id DESC)` and unread.
- More only if a step needs one; for example, a time zone on users, depending on question 2.

## Open questions (please answer with the go-ahead)

1. **Dependencies and images outside spec §2:**
   - `spring-kafka`, plus the Testcontainers Kafka module;
   - `spring-boot-starter-websocket` (STOMP is named in §8 but not in §2);
   - `micrometer-registry-prometheus` (needed for `/actuator/prometheus` in §11);
   - for the browser, `@stomp/stompjs`, or I write a minimal STOMP client by hand (recommended: `@stomp/stompjs`);
   - images: `apache/kafka` (KRaft), `prom/prometheus`, `grafana/grafana`, `grafana/k6`, and FFmpeg via apt in the backend image.

   Which are approved?
2. **On This Day notifications.** These need a "morning" for each user. Recommended: store the browser's time zone on the user and send at 09:00 local time. The alternative is a fixed 09:00 UTC for everyone.
3. **Like notifications.** One notification per like, or grouped ("Ana and 3 others liked your memory") per memory per hour? Recommended: grouped.
4. **Load-test tokens.** Is a test-only token-minting command OK? It would be disabled unless a load-test profile is set. The alternative is k6 logging in through a raised rate limit in a dedicated load-test Compose profile.
5. **Fan-out-on-read code.** Keep it as the cold-start rebuild and test oracle (recommended), or remove it once fan-out-on-write ships?
