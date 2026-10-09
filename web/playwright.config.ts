import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end flows from spec §12 Phase 1, run against the Docker Compose stack (`docker compose up`).
 * E2E_BASE_URL overrides the web address; E2E_COMPOSE_PROJECT names the Compose project if it is not the default.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 30_000 },
  // The flows share the stack's per-IP rate limits, so run them one at a time.
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'mobile-chromium', use: { ...devices['Pixel 7'] } }],
});
