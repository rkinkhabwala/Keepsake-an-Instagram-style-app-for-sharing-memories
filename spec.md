# Keepsake — an Instagram-style app for sharing memories

> Working name: **Keepsake**. A photo/video sharing app where every post is a *memory*: it has a date it happened, an optional place, people who were there, and a visibility level. Think Instagram's feed + profile grid, plus memory-first features (On This Day, albums, a map of where memories happened).

This spec is written to be implemented incrementally with Claude Code. Build one phase at a time; each phase ends in a runnable, tested app.

---

## 1. Goals and non-goals

**Goals**
- Users sign up, post memories (1–10 photos or a short video + caption), follow each other, and scroll a home feed.
- Memories carry *when* (the date it happened, not just upload time), *where*, *who*, and *who can see it*.
- Memories resurface: "On This Day", albums, and a map.
- Clean, production-style architecture that runs fully locally with Docker Compose.

**Non-goals (v1)**
- Cloud deployment, mobile native apps, DMs, Stories, Reels-style algorithmic discovery, ads, payments.

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| API | REST (JSON), OpenAPI via springdoc |
| Auth | Spring Security, JWT access token (15 min) + rotating refresh token (httpOnly cookie, 14 days), BCrypt |
| Database | PostgreSQL 16, Flyway migrations, Spring Data JPA |
| Cache / counters / feed | Redis 7 |
| Media storage | MinIO (S3-compatible, local), accessed with AWS SDK v2 |
| Image processing | Thumbnailator (resize) + metadata-extractor (EXIF) |
| Video processing | FFmpeg (poster frame + 720p transcode) — Phase 3 |
| Async work | Spring `@Async` in Phase 1–2; Kafka in Phase 3 |
| Web client | React 18 + TypeScript + Vite, TanStack Query, React Router, Tailwind |
| Map | Leaflet + OpenStreetMap tiles |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres, Redis, MinIO), Vitest + React Testing Library, Playwright for 3 critical E2E flows |
| Local infra | Docker Compose (postgres, redis, minio, app, web; kafka added in Phase 3) |

---

## 3. Repository layout

```
keepsake/
├── spec.md
├── docker-compose.yml
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/keepsake/
│       ├── KeepsakeApplication.java
│       ├── config/          # security, S3, redis, cors, openapi
│       ├── auth/            # signup, login, refresh, logout
│       ├── user/            # profiles, follow graph
│       ├── memory/          # posts (memories), media, visibility
│       ├── media/           # upload, processing, storage
│       ├── feed/            # home feed assembly
│       ├── engagement/      # likes, comments, saves
│       ├── album/           # collections of memories (Phase 2)
│       ├── resurface/       # On This Day (Phase 2)
│       ├── notification/    # Phase 3
│       ├── search/          # Phase 2
│       └── common/          # errors, pagination, ids, auditing
│   └── src/main/resources/db/migration/   # Flyway V1__*.sql ...
└── web/
    ├── package.json
    └── src/
        ├── api/             # typed client, query hooks
        ├── pages/           # Feed, Profile, MemoryDetail, Create, Login, Signup, Album, Map, OnThisDay
        ├── components/      # MemoryCard, Carousel, Avatar, LikeButton, CommentList, Uploader
        └── lib/
```

Package-by-feature. Each feature has `controller / service / repository / dto / entity`. Controllers never return entities — always DTOs.

---

## 4. Domain model

IDs are UUIDv7 (time-ordered, good for cursor pagination). All tables have `created_at`, `updated_at`.

**users**
- `id`, `username` (unique, 3–30 chars, `[a-z0-9._]`), `email` (unique), `password_hash`, `display_name`, `bio` (≤150), `avatar_key`, `is_private` (bool), `created_at`

**follows**
- `follower_id`, `followee_id`, `status` (`ACCEPTED` | `PENDING`), `created_at` — PK (follower_id, followee_id)
- Following a private account creates `PENDING` until approved.

**memories**
- `id`, `author_id`, `caption` (≤2,200), `occurred_on` (DATE, required, defaults to EXIF date of first photo, else today), `location_name`, `lat`, `lng` (nullable), `visibility` (`PUBLIC` | `FOLLOWERS` | `CLOSE_FRIENDS` | `ONLY_ME`), `like_count`, `comment_count`, `status` (`PROCESSING` | `READY` | `FAILED`), `deleted_at` (soft delete)

**memory_media**
- `id`, `memory_id`, `position` (0–9), `type` (`IMAGE` | `VIDEO`), `original_key`, `display_key` (1080px), `thumb_key` (320px), `width`, `height`, `duration_ms`, `blurhash`

**memory_people** (people tagged as "was there")
- `memory_id`, `user_id`

**close_friends**
- `owner_id`, `friend_id`

**likes** — PK (`user_id`, `memory_id`), `created_at`

**comments**
- `id`, `memory_id`, `author_id`, `parent_id` (nullable, one level of replies), `body` (≤500), `deleted_at`

**saves** — PK (`user_id`, `memory_id`)

**albums** (Phase 2)
- `id`, `owner_id`, `title`, `description`, `cover_memory_id`, `visibility`
- **album_memories**: `album_id`, `memory_id`, `position`
- **album_collaborators**: `album_id`, `user_id`, `role` (`EDITOR` | `VIEWER`) — shared albums, e.g. a trip
  - When a collaborator leaves, is removed by the owner, or is in a block with the owner, the memories they added leave the album with them.

**hashtags** / **memory_hashtags** (Phase 2)

**notifications** (Phase 3)
- `id`, `recipient_id`, `actor_id`, `type` (`LIKE` | `COMMENT` | `FOLLOW` | `FOLLOW_REQUEST` | `TAGGED` | `ON_THIS_DAY`), `memory_id`, `read_at`

**refresh_tokens**
- `id`, `user_id`, `token_hash`, `family_id`, `expires_at`, `revoked_at` — reuse of a revoked token revokes the whole family.

### Indexes worth having from day one
- `memories (author_id, created_at DESC) WHERE deleted_at IS NULL`
- `memories (author_id, occurred_on)` — for On This Day
- `follows (followee_id, status)` and `follows (follower_id, status)`
- `comments (memory_id, created_at)`
- `memory_people (user_id)`

---

## 5. Visibility rules (the most important business logic)

A viewer V can see memory M by author A iff M is `READY`, not deleted, and:

| visibility | rule |
|---|---|
| `PUBLIC` | A is not private, **or** V follows A (ACCEPTED), **or** V == A |
| `FOLLOWERS` | V follows A (ACCEPTED) or V == A |
| `CLOSE_FRIENDS` | V is in A's close_friends or V == A |
| `ONLY_ME` | V == A |

Implement once in `MemoryAccessPolicy` and use it everywhere (feed, profile grid, detail, comments, likes, album views, search, map). Never re-derive it inline. Unit-test every row of this table, including private-account and pending-follow cases. Unauthorized access returns **404**, not 403 (don't leak existence).

---

## 6. Media pipeline

**Upload flow (direct-to-storage, server never proxies bytes):**
1. `POST /api/media/upload-urls` with `[{contentType, sizeBytes}]` → server validates (JPEG/PNG/HEIC/WebP ≤ 20 MB; MP4/MOV ≤ 100 MB, ≤ 60 s) and returns presigned MinIO PUT URLs + `uploadId`s.
2. Client PUTs files directly to MinIO.
3. `POST /api/memories` with caption, occurred_on, location, visibility, tagged users, and ordered `uploadId`s → creates memory in `PROCESSING`, returns 202 with the id.
4. Async processor per media item: verify the object exists and its magic bytes match the declared type, read EXIF (date taken, GPS) then **strip all EXIF** from derived images, generate `display` (1080px long edge, WebP) and `thumb` (320px square crop), compute blurhash. Videos (Phase 3): poster frame + 720p H.264.
5. When all items succeed → memory `READY`; any failure → `FAILED`, author sees a retry option.

**Serving:** short-lived presigned GET URLs (10 min) in DTOs, or a `/media/{key}` redirect endpoint. Originals are never served to non-authors.

**Privacy note:** if EXIF has GPS, *offer* it as the memory's location in the create form — never attach it silently.

---

## 7. Feed

**Phase 1 — fan-out on read (simple, correct):**
Home feed = memories from people V follows (ACCEPTED) + V's own, filtered by `MemoryAccessPolicy`, ordered by `created_at DESC`, cursor-paginated (cursor = last memory id; UUIDv7 sorts by time). Page size 20.

**Phase 3 — fan-out on write (scale):**
On `READY`, push memory id into each eligible follower's Redis sorted set `feed:{userId}` (score = timestamp, capped at 800 entries). Accounts with > 10k followers are *not* fanned out; their posts are merged in at read time (hybrid model). Visibility is re-checked at read time so later changes (unfollow, close-friends removal, visibility edit) are respected.

Counters (likes/comments) are incremented in Redis and flushed to Postgres every few seconds; the DB columns are the source of truth on cache miss.

---

## 8. REST API

All under `/api`. Auth header `Authorization: Bearer <jwt>`. Errors use RFC 7807 problem+json. List endpoints use `?cursor=&limit=` and return `{ items, nextCursor }`.

**Auth**
- `POST /auth/signup` · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout`

**Users**
- `GET /users/me` · `PATCH /users/me` (display name, bio, avatar uploadId, is_private)
- `GET /users/{username}` — profile + counts + relationship to viewer
- `GET /users/{username}/memories` — grid (thumbs only)
- `POST /users/{username}/follow` · `DELETE /users/{username}/follow`
- `GET /users/{username}/followers` · `GET /users/{username}/following`
- `GET /follow-requests` · `POST /follow-requests/{userId}/accept` · `.../decline`
- `GET /close-friends` · `PUT /close-friends/{userId}` · `DELETE /close-friends/{userId}`

**Memories**
- `POST /media/upload-urls`
- `POST /memories` · `GET /memories/{id}` · `PATCH /memories/{id}` (caption, occurred_on, location, visibility, tagged people) · `DELETE /memories/{id}`
- `GET /feed`
- `PUT /memories/{id}/like` · `DELETE /memories/{id}/like`
- `GET /memories/{id}/comments` · `POST /memories/{id}/comments` · `DELETE /comments/{id}`
- `PUT /memories/{id}/save` · `DELETE /memories/{id}/save` · `GET /saved`
- `GET /users/{username}/tagged` — memories this person was tagged in

**Phase 2**
- `GET /resurface/on-this-day` — viewer's own memories (and ones they're tagged in) with the same month/day in prior years, grouped by year
- `GET /memories/map?bbox=` — viewer-visible memories with coordinates inside a bounding box
- Albums: `POST /albums` · `GET /albums/{id}` · `PATCH` · `DELETE` · `POST /albums/{id}/memories` · `DELETE /albums/{id}/memories/{memoryId}` · `POST /albums/{id}/collaborators`
- `GET /search?q=` — users (prefix on username/display name) and hashtags
- `GET /hashtags/{tag}/memories`

**Phase 3**
- `GET /notifications` · `POST /notifications/read`
- `GET /ws` — WebSocket (STOMP) for live notifications

---

## 9. Web client screens

1. **Login / Signup**
2. **Home feed** — infinite scroll of `MemoryCard`s: avatar + username, swipeable carousel (blurhash placeholder → image), "happened on {occurred_on}" + location chip, like (double-tap too), comment, save, caption with expandable "more", latest 2 comments.
3. **Create memory** — drag-and-drop up to 10 files, reorder, per-file upload progress; caption; date picker prefilled from EXIF; location (prefill offer from EXIF GPS, or free text); tag people (username autocomplete); visibility selector with plain-language descriptions. Shows "Processing…" state after submit.
4. **Memory detail** — full carousel, comments with one level of replies, edit/delete for author.
5. **Profile** — avatar, counts, bio, follow/requested/following button, 3-column grid, tabs: Memories · Tagged · Saved (own only) · Albums (Phase 2). Private account shows a lock state to non-followers.
6. **Edit profile / Settings** — private toggle, close friends list, follow requests.
7. **On This Day** (Phase 2) — "1 year ago", "3 years ago" sections.
8. **Map** (Phase 2) — Leaflet map with clustered thumbnails.
9. **Album** (Phase 2) — cover, title, grid, collaborators.
10. **Notifications** (Phase 3) — bell with unread badge.

Responsive: mobile-first single column, max content width 470px for the feed, profile grid up to 935px. Light and dark mode.

---

## 10. Security and abuse basics

- Rate limits (Bucket4j + Redis): login 5/min/IP, signup 3/hour/IP, create memory 30/hour/user, comments 60/hour/user.
- Validate and sanitize all text; render captions/comments as plain text (linkify @mentions and #hashtags client-side only).
- File type checked by magic bytes, not extension; EXIF stripped from everything served.
- CORS restricted to the web origin. Secrets via env vars; `.env.example` committed, `.env` git-ignored.
- Block list (Phase 2): blocked users can't see, follow, comment, or tag each other — fold into `MemoryAccessPolicy`.
- Account deletion deletes memories, media objects, likes, comments, and tokens.

---

## 11. Observability

- Spring Boot Actuator (`/actuator/health`, `/actuator/prometheus`).
- Structured JSON logs with a request id (MDC) propagated to async jobs.
- Phase 3: Prometheus + Grafana in Compose with dashboards for p95 latency on `/feed`, media processing time, and queue lag.

---

## 12. Phases and acceptance criteria

### Phase 1 — Core app (MVP)
Scope: auth, profiles, follow (incl. private accounts + requests), close friends, create memory with photos, processing pipeline (images only), visibility rules, feed (fan-out on read), memory detail, likes, comments, saves, tagged people, web client screens 1–6.

Done when:
- `docker compose up` brings up the whole stack; a seed script creates 5 users, follows, and ~50 memories with sample images.
- Every visibility rule in §5 has a passing unit test, plus an integration test (Testcontainers) for feed and profile grid.
- Playwright E2E passes for: signup → create memory → see it in own feed; user B follows private user A → A approves → B sees A's FOLLOWERS memory; CLOSE_FRIENDS memory hidden from a non-close-friend follower.
- Feed p95 < 200 ms locally with the seed data.

### Phase 2 — Memory features
Scope: On This Day, map view, albums (incl. shared albums with collaborators), hashtags, search, block list, edit history of `occurred_on` not required.

Done when:
- On This Day returns correct results across leap years (Feb 29 memories surface on Feb 28 in non-leap years).
- Map endpoint only ever returns memories the viewer is allowed to see (test with mixed visibility).
- An album collaborator can add their own memories; a viewer of the album only sees memories they'd be allowed to see individually.

### Phase 3 — Scale and realtime
Scope: Kafka for media + fan-out events, fan-out-on-write feed with celebrity hybrid, Redis counters with periodic flush, video support via FFmpeg, notifications over WebSocket, Prometheus/Grafana, a k6 load test.

Done when:
- k6 test with 1,000 virtual users scrolling the feed sustains p95 < 300 ms on a dev laptop.
- Unfollowing someone removes their memories from your feed on the next request, even if already fanned out.
- Notifications arrive in the open browser within 2 s of the triggering action.

---

## 13. Working agreement for Claude Code

- Implement one phase at a time; within a phase, go feature by feature (auth → users/follows → media → memories → feed → engagement → web).
- Write the Flyway migration and tests alongside each feature, not at the end.
- Keep `MemoryAccessPolicy` as the single source of truth for who can see what.
- After each feature: run `mvn verify` and `npm test`, and keep the README's "how to run" section current.
- Ask before adding any dependency not listed in §2.