# Keepsake

Keepsake is a local-first memory sharing app. The Phase 1 stack is a Java 21 / Spring Boot backend, React / TypeScript web client, PostgreSQL, Redis, and MinIO.

## How to run

1. Install Java 21, Maven, Node.js 22+, npm, and Docker Compose.
2. Copy `.env.example` to `.env` and replace the local secrets if exposing services beyond your machine.
3. From the repository root, run `docker compose up --build`.
4. Open the web client at <http://localhost:5173>, the API at <http://localhost:8080>, and MinIO Console at <http://localhost:9001>.

On first start the backend seeds demo data in the background (about 30 seconds): five users, follows, close friends, and 50 memories with generated photos, likes, and comments. Log in as `ana`, `ben`, `cleo`, `dev`, or `eli`, all with the password `keepsake-demo`. `cleo` is a private account, and `ana` has a pending request to follow her. Seeding is skipped when the demo users already exist; set `SEED_DEMO_DATA=false` in `.env` to turn it off.

The API health endpoint is `/actuator/health`. PostgreSQL, Redis, and MinIO data are persisted in named Compose volumes.

## Development checks

- Backend: `cd backend && mvn verify`
- Web client: `cd web && npm ci && npm test -- --run && npm run build`

## Configuration

All local configuration is read from environment variables. `.env.example` lists the Compose variables; `.env` is local-only and git-ignored.

Browsers upload and download media directly from MinIO using presigned URLs, so `MINIO_PUBLIC_ENDPOINT` must be the MinIO address as the browser sees it (default `http://localhost:9000`). The backend reaches MinIO inside Compose at `http://minio:9000`.

MinIO no longer publishes free container images, so Compose and the integration tests use `pgsty/minio`, a community rebuild of the upstream release, pinned to a release tag.
