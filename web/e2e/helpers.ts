import { execSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';
import { type Browser, type Page, expect } from '@playwright/test';

/**
 * The stack enforces real per-IP signup/login limits (spec §10), and every test user signs up from this machine.
 * Clear the limit buckets in the stack's Redis so runs are repeatable without loosening the limits themselves.
 */
export function resetRateLimits() {
  const project = process.env.E2E_COMPOSE_PROJECT ? `-p ${process.env.E2E_COMPOSE_PROJECT}` : '';
  execSync(
    `docker compose ${project} exec -T redis sh -c "redis-cli --scan --pattern 'keepsake:rate:*' | xargs -r redis-cli del"`,
    { cwd: resolve(dirname(fileURLToPath(import.meta.url)), '..', '..'), stdio: 'ignore', timeout: 30_000 },
  );
}

/** Waits until the stack answers API requests through the web proxy (no 502 while the backend starts). */
export async function waitForStack(baseURL: string) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseURL}/api/auth/refresh`, { method: 'POST' });
      if (response.status < 500) return;
    } catch {
      // Not reachable yet.
    }
    await new Promise((done) => setTimeout(done, 1000));
  }
  throw new Error(`Keepsake did not become ready at ${baseURL}`);
}

export function uniqueName(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`.slice(0, 30);
}

let photoPath: string | null = null;

/** A small gradient PNG written once per run, so the tests carry no binary fixtures. */
export function samplePhoto(): string {
  if (photoPath) return photoPath;
  const width = 320;
  const height = 240;
  const rows: Buffer[] = [];
  for (let y = 0; y < height; y++) {
    const row = Buffer.alloc(1 + width * 3);
    for (let x = 0; x < width; x++) row.set([(x * 255) / width, (y * 255) / height, 140], 1 + x * 3);
    rows.push(row);
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.set([8, 2, 0, 0, 0], 8);
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  photoPath = join(mkdtempSync(join(tmpdir(), 'keepsake-e2e-')), 'photo.png');
  writeFileSync(photoPath, png);
  return photoPath;
}

function chunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function crc32(bytes: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/** Signs up a new user in a fresh browser context and returns their page, on the home feed. */
export async function newUser(browser: Browser, prefix: string): Promise<{ page: Page; username: string }> {
  const context = await browser.newContext();
  const page = await context.newPage();
  const username = uniqueName(prefix);
  await page.goto('/signup');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Display name').fill(prefix.toUpperCase());
  await page.getByLabel('Email').fill(`${username}@e2e.test`);
  await page.getByLabel('Password').fill('keepsake-e2e-password');
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByRole('navigation', { name: 'Main mobile' })).toBeVisible();
  return { page, username };
}

/** Creates a memory through the create screen and waits until it has finished processing. */
export async function createMemory(page: Page, caption: string, visibility: RegExp): Promise<string> {
  await page.goto('/new');
  await page.getByLabel('Choose photos').setInputFiles(samplePhoto());
  await page.getByLabel('Caption').fill(caption);
  await page.getByRole('radio', { name: visibility }).check();
  const share = page.getByRole('button', { name: 'Share memory' });
  await expect(share).toBeEnabled();
  await share.click();
  await page.waitForURL(/\/m\//);
  // Processing finishes on its own; the like button only appears once the memory is READY.
  await expect(page.getByRole('button', { name: 'Like' })).toBeVisible();
  await expect(page.getByText(caption)).toBeVisible();
  return page.url();
}
