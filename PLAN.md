# Keepsake Phase 2 implementation plan

Phase 1 is complete and merged into `main` (its plan is in git history). This plan covers spec §12 Phase 2 only: block list, hashtags, search, On This Day, map, and albums. Nothing from Phase 3.

Same working rules as Phase 1: one feature at a time on a `phase-2` branch, each with its Flyway migration and tests, `mvn verify` and `npm test` (and the Playwright suite) green before the next, small scoped commits.

Dependencies: everything comes from spec §2, plus the approved `leaflet.markercluster`. Search uses plain Postgres B-tree indexes (no trigram, no PostGIS). The open questions at the end list the two type-only packages that still need approval.

## Build order

Block list comes first because it changes `MemoryAccessPolicy`, which every later list and endpoint relies on.

1. **Block list.** Add `PUT /api/blocks/{userId}`, `DELETE /api/blocks/{userId}`, and `GET /api/blocks` (cursor-paginated). §10 asks for blocking but §8 lists no endpoints, so these names are a proposal.
   - **Policy:** blocking goes into `MemoryAccessPolicy`, both the Java `decide` and `VISIBLE_TO_VIEWER_SQL`. A block in *either direction* between viewer and author hides the memory regardless of visibility, follows, or close friends. The author still sees their own memories.
   - **Blocking removes the relationship:** follows and pending requests both ways, close-friend entries both ways, and tags between the two users.
   - **While a block exists:**
     - neither user can follow the other;
     - neither can comment on the other's memories (already covered by the policy, which turns the comment into a 404);
     - neither can tag the other, and tagging is rejected as "Unknown user" so the block is not revealed;
     - each one's profile returns 404 to the other;
     - each one's comments and follower or following entries are hidden from the other.
2. **Hashtags.** Parse `#tags` from captions on create and edit (letters, numbers and `_`, up to 100 characters, lowercased, NFC-normalized), and store them in `hashtags` and `memory_hashtags`. Add `GET /api/hashtags/{tag}/memories`, a grid filtered by the policy's SQL and paged by memory id. The web client turns `#tags` into links to that page.
3. **Search.** `GET /api/search?q=` returns users matched by prefix on username or display name, plus hashtags matched by prefix, up to 20 of each.
   - Matching uses B-tree `text_pattern_ops` indexes on lowercased columns.
   - Users in a block relationship with the viewer are excluded.
   - The query is trimmed, a leading `@` or `#` is stripped, and LIKE wildcards are escaped.
   - Display-name matching is a prefix of the whole name, which is what a plain index supports.
4. **On This Day.** `GET /api/resurface/on-this-day?date=YYYY-MM-DD` returns the viewer's own memories and the ones they're tagged in whose `occurred_on` has the same month and day as `date` in an earlier year, grouped by year, newest year first.
   - Tagged memories must also pass the policy: being tagged doesn't grant access.
   - `date` is the viewer's local date and defaults to today in UTC. Any date is safe to accept, since only the viewer's own visible memories can come back.
   - Leap-year rule: on Feb 28 of a *non-leap* year, memories from Feb 29 also appear. In a leap year they appear only on Feb 29.
   - The date matching lives in a small pure function, unit-tested across leap and non-leap years.
5. **Map.** `GET /api/memories/map?bbox=minLng,minLat,maxLng,maxLat` returns memories with coordinates inside the box, filtered by the policy's SQL and capped at 500, newest first.
   - A box that crosses the antimeridian (`minLng > maxLng`) is handled.
   - The bbox is validated.
   - A partial B-tree index covers `(lat, lng)` where both are set.
   - Which memories appear (beyond what the policy allows) is open question 1 below.
6. **Albums.** Endpoints from §8: `POST /api/albums`, `GET`/`PATCH`/`DELETE /api/albums/{id}`, `POST /api/albums/{id}/memories`, `DELETE /api/albums/{id}/memories/{memoryId}`, `POST /api/albums/{id}/collaborators`. Plus two §8 doesn't list but the screens need: `DELETE /api/albums/{id}/collaborators/{userId}`, and `GET /api/users/{username}/albums` for the profile's Albums tab (§9).
   - **Who can see an album:** an album has a visibility (same four values) relative to its owner. Collaborators can always see it. The album-level rule reuses the policy's visibility function (generalized to "owned content with a visibility") and is not re-derived.
   - **What viewers see inside:** each memory in an album is filtered individually through `MemoryAccessPolicy` for the viewer (§12 acceptance). The cover is shown only if the viewer may see that memory; otherwise the first visible memory is used.
   - **Adding and removing:** the owner and EDITOR collaborators can add only memories they authored. The owner can remove anything, and an editor can remove what they added. VIEWER collaborators can see the album but not change it.
   - **Blocked users:** they can't be added as collaborators, and blocking removes an existing collaboration.
7. **Web screens 7–9 and Phase 2 UI.**
   - On This Day ("1 year ago", "3 years ago" sections).
   - Map: plain Leaflet API plus `leaflet.markercluster`, OSM tiles, clustered thumbnail markers, refetching the bbox on pan and zoom. It uses no `react-leaflet`, which isn't in §2.
   - Album page (cover, title, grid, collaborators), with create and edit and "Add to album" from a memory.
   - An Albums tab on profiles.
   - Header search with user and hashtag results, and a hashtag page.
   - Block and unblock from a profile menu, and a blocked-accounts list in Settings.
   - Vitest coverage for each.
8. **Seed data.**
   - Give about a third of the demo memories coordinates and hashtags.
   - Add a Feb 29 memory (for the leap-year rule).
   - Add a shared album: `ana` owns it, `ben` is an EDITOR, and it contains memories of mixed visibility.
   - Add one block between two demo users.
   - Seeding stays idempotent.
9. **Verification.**
   - Integration tests for each §12 Phase 2 criterion.
   - Re-run `mvn verify`, `npm test`, and the three Phase 1 Playwright flows.
   - Add one Playwright flow for the album collaborator case, since it crosses users and screens.
   - Check that feed p95 is still under 200 ms with the block checks in the policy.

## Migrations

- `V8__blocks.sql`: `blocks (blocker_id, blocked_id, created_at, updated_at)`, with PK, a check that blocker and blocked differ, and an index on `blocked_id`.
- `V9__hashtags.sql`: `hashtags (id, tag UNIQUE, created_at, updated_at)` and `memory_hashtags (memory_id, hashtag_id)` with an index on `hashtag_id`.
- `V10__search_indexes.sql`: `text_pattern_ops` indexes on `lower(username)`, `lower(display_name)`, and `hashtags.tag`.
- `V11__map_index.sql`: partial index on `memories (lat, lng) WHERE lat IS NOT NULL AND deleted_at IS NULL`.
- `V12__albums.sql`:
  - `albums (id, owner_id, title ≤100, description ≤500, cover_memory_id ON DELETE SET NULL, visibility, timestamps)`;
  - `album_memories (album_id, memory_id, position, added_by, timestamps; PK (album_id, memory_id))`;
  - `album_collaborators (album_id, user_id, role EDITOR|VIEWER, timestamps)`;
  - account deletion and memory deletion cascade correctly.

## Tests per feature

- **Block list:**
  - New rows in `MemoryAccessPolicyTest`'s visibility table for both directions (viewer blocked author, author blocked viewer). They cover every visibility on public and private accounts, and accepted follower, pending follower, and close-friend relations, to show the block overrides each one.
  - The SQL parity test gains a block dimension (none, viewer→author, author→viewer), so the Java and SQL rules are compared across every combination.
  - Integration tests: block removes follows, close friends, and tags; follow, comment, and tag attempts fail without revealing the block; profiles, lists, and comment threads hide the other user; the feed and grids filter both ways.
- **Hashtags:** parsing unit tests (Unicode, case, duplicates, limits) and an integration test that the hashtag grid is visibility-filtered and updates on caption edits.
- **Search:** prefix matching on username, display name, and hashtag; wildcard escaping; blocked users excluded; and a check that the query plan uses the indexes.
- **On This Day:** unit tests for the date rule (a Feb 29, 2020 memory appears on Feb 28 in 2021, 2022, and 2023, and on Feb 29 in 2024, but not on Feb 28, 2024 or Mar 1, 2021), and integration tests for grouping, tagged memories through the policy, and excluding the current year.
- **Map:** an integration test with mixed visibility and authors showing the endpoint returns only what the viewer may see; bbox edges, antimeridian, invalid bbox, and the cap.
- **Albums:** an integration test that a collaborator can add their own memories; an album viewer sees only memories they could see individually (mixed visibility, private authors, blocks); the access matrix for owner, editor, viewer, and stranger; the cover-visibility fallback; and deletion cascades.

## Open questions (please answer with the go-ahead)

1. **Map scope.** Spec §8 says "viewer-visible memories". Read literally, that includes public memories from strangers anywhere in the world, a discovery surface the spec's non-goals steer away from. **Recommended:** the viewer's own memories, memories of accounts they follow, and memories they're tagged in, all still filtered by the policy.
2. **Album collaborators.** Who can be added, and is there an invite to accept? **Recommended:** the owner can add people who follow them (ACCEPTED), added directly with no invite flow, and any collaborator can leave.
3. **Map type packages.** May I add the dev-only TypeScript typings `@types/leaflet` and `@types/leaflet.markercluster`? They add no runtime code. Without them the map code needs local type declarations.
4. **Block endpoints and effects.** Are the proposed `/api/blocks` endpoints and the effects in step 1 what you want? In particular: hiding each other's comments on third-party memories, and permanently removing tags between the two users when one blocks the other.
