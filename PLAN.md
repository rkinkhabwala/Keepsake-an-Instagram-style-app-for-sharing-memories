# Keepsake Phase 1 implementation plan

Implement one feature at a time, run `mvn verify` after each backend feature, and run `npm test` after the web client exists and after each later feature. Add only dependencies listed in `spec.md` §2, plus the HEIC-capable ImageIO dependency approved for this work. No Phase 2 or Phase 3 features.

## Build order

1. **Project scaffolding** — Create the Java 21 / Spring Boot 3 Maven backend, React 18 / TypeScript / Vite web client, Compose services (Postgres, Redis, MinIO, backend, web), `.env.example`, ignored `.env`, and run instructions in `README.md`. Verify Maven and web test/build baselines and validate Compose configuration.
2. **Common layer** — Implement UUIDv7 generation, RFC 7807 errors, `{ items, nextCursor }` cursor pagination, and request-ID propagation in structured logs. Unit-test IDs, error responses, pagination boundaries, and request-ID behavior.
3. **Auth** — Add signup/login, BCrypt, 15-minute access JWTs, rotating 14-day refresh tokens in an httpOnly cookie with family revocation, and §10 rate limits. Add the first Flyway migration (`V1`) for users and refresh tokens. Test validation, authentication, rotation/reuse revocation, cookies, and limits, with Postgres/Redis integration tests.
4. **Users and follow graph** — Add profile operations, private accounts, follow requests and acceptance/decline, and close friends. Add `V2` for follows and close-friend relations. Test public/private profile and follow flows, including pending requests.
5. **MemoryAccessPolicy** — Implement the §5 table as the only visibility decision point, including private-account and pending-follow cases; unauthorized access must be 404. No migration. Unit-test every visibility row and relevant relationship state.
6. **Image media pipeline** — Add presigned uploads, size/type checks, magic-byte verification, EXIF read-then-strip, 1080px WebP display, 320px square thumbnail, blurhash, and asynchronous processing states. Add `V3` for upload tracking. Test limits, invalid bytes, EXIF removal, output dimensions/format, blurhash, and PROCESSING→READY/FAILED using MinIO integration tests.
7. **Memories** — Add create/get/edit/soft-delete, ordered media, tagged people, and `occurred_on` defaulting from first-image EXIF or today. Add `V4` for memories, media, and people plus the specified indexes/constraints. Test lifecycle, authorization through the policy, tags, and date defaults.
8. **Feed** — Implement fan-out-on-read, accepted-following plus own memories, policy filtering, created-time ordering, and cursor pages of 20. No migration. Integration-test ordering, filtering, cursors, and feed p95 below 200 ms with seed-scale data.
9. **Engagement** — Add likes, one-level comments/replies, saves, and the tagged tab, each protected by the shared policy where memory access is involved. Add `V5` for likes, comments, and saves. Test create/remove flows, reply depth, visibility enforcement, and tagged results.
10. **Web screens 1–6** — Build login/signup, feed, memory creation, detail, profile, and settings/close-friends/follow-requests screens with mobile-first styling, light/dark mode, TanStack Query, and blurhash placeholders. Add focused Vitest/React Testing Library coverage; run `npm test`.
11. **Seed data** — Add a repeatable seed script for five users, accepted and pending follows (including a private account), approximately 50 memories, and generated local sample images. Verify counts, relationships, and processed media.
12. **End-to-end and acceptance verification** — Add the three §12 Playwright flows. Run them against the Compose stack, verify `docker compose up` yields the seeded runnable app, rerun `mvn verify` and `npm test`, and measure feed p95 locally against the stated threshold.

## Migration sequence

- `V1__users_and_refresh_tokens.sql` — users and refresh-token records, including unique username/email constraints and token-family fields.
- `V2__follows_and_close_friends.sql` — follow state and close-friend relations, with lookup indexes.
- `V3__media_uploads.sql` — upload records needed to associate presigned uploads with authenticated owners and media processing.
- `V4__memories_and_media.sql` — memories, memory media, and tagged people, including status/visibility constraints and the §4 memory indexes.
- `V5__engagement.sql` — likes, comments, and saves, including uniqueness, one-level-reply enforcement, and the comments index.

Common-layer and feed changes do not require schema migrations.
