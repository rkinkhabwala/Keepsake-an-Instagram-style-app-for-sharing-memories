# Keepsake

Keepsake is a local-first memory sharing app. The Phase 1 stack is a Java 21 / Spring Boot backend, React / TypeScript web client, PostgreSQL, Redis, and MinIO.

## How to run

1. Install Java 21, Maven, Node.js 22+, npm, and Docker Compose.
2. Copy `.env.example` to `.env` and replace the local secrets if exposing services beyond your machine.
3. From the repository root, run `docker compose up --build`, or `SEED_DEMO=true docker compose up --build` to also create demo data.
4. Open the web client at <http://localhost:5173> and MinIO Console at <http://localhost:9001>. The API is also reachable directly at <http://localhost:8080>. Compose binds the web client and the API to 127.0.0.1, so they are reachable only from this machine.

Demo data is opt-in, because every demo account shares a published password. With `SEED_DEMO=true` (on the command line or in `.env`), the backend seeds in the background on first start (about 30 seconds): five users, follows, close friends, and 50 memories with generated photos, likes, and comments. Log in as `ana`, `ben`, `cleo`, `dev`, or `eli`, all with the password `keepsake-demo`. `cleo` is a private account, and `ana` has a pending request to follow her. Seeding is skipped when the demo users already exist.

The API health endpoint is `/actuator/health`. PostgreSQL, Redis, and MinIO data are persisted in named Compose volumes.

## Metrics and dashboards

Prometheus and Grafana run behind the `observability` Compose profile:

```
docker compose --profile observability up --build
```

- Grafana: <http://localhost:3000> opens the provisioned **Keepsake overview** dashboard (feed p50/p95 latency, feed request rate, media processing time, and media queue lag). Viewing needs no login; the admin password is `GRAFANA_ADMIN_PASSWORD` from `.env`.
- Prometheus: <http://localhost:9090>, scraping the backend's `/actuator/prometheus` every 5 seconds.
- Both are bound to 127.0.0.1. The metrics endpoint is reachable only inside Compose and from this machine.

## Development checks

- Backend: `cd backend && mvn verify`
- Web client: `cd web && npm ci && npm test -- --run && npm run build`
- End-to-end (Playwright, against the running stack): start it with `docker compose up -d --build --wait`, then `cd web && npx playwright install chromium && npm run e2e`. The tests create their own users and clear the stack's rate-limit counters in Redis before each flow; set `E2E_COMPOSE_PROJECT` if you started Compose with `-p`, and `E2E_BASE_URL` if the web client is not on <http://localhost:5173>.

## Configuration

All local configuration is read from environment variables. `.env.example` lists the Compose variables; `.env` is local-only and git-ignored.

Per-IP rate limits (login, signup) use the real client address. The web container's nginx passes it in `X-Forwarded-For`, and the backend believes that header only from the hosts listed in `KEEPSAKE_TRUSTED_PROXIES` (`web` in Compose); from anyone else it is ignored, so clients cannot choose their own address.

Browsers upload and download media directly from MinIO using presigned URLs, so `MINIO_PUBLIC_ENDPOINT` must be the MinIO address as the browser sees it (default `http://localhost:9000`). The backend reaches MinIO inside Compose at `http://minio:9000`.

MinIO no longer publishes free container images, so Compose and the integration tests use `pgsty/minio`, a community rebuild of the upstream release, pinned to a release tag.
