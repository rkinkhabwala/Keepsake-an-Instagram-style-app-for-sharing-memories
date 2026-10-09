import { expect, test } from '@playwright/test';
import { newUser, resetRateLimits, uniqueName, waitForStack } from './helpers';

test.beforeAll(({ baseURL }) => waitForStack(baseURL!));
test.beforeEach(() => resetRateLimits());

test('a memory with coordinates appears on the map and opens from its marker', async ({ browser }) => {
  const { page } = await newUser(browser, 'e2e_map');
  const caption = `Mapped ${uniqueName('c')}`;

  // The create screen only offers coordinates from photo GPS, so set them through the API with the session.
  await page.goto('/new');
  const memoryId = await page.evaluate(async (text) => {
    const session = await (await fetch('/api/auth/refresh', { method: 'POST' })).json();
    const headers = { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json' };
    const canvas = Object.assign(document.createElement('canvas'), { width: 32, height: 32 });
    canvas.getContext('2d')!.fillRect(0, 0, 32, 32);
    const photo = await new Promise<Blob>((done) => canvas.toBlob((blob) => done(blob!), 'image/png'));
    const [slot] = await (await fetch('/api/media/upload-urls', {
      method: 'POST', headers, body: JSON.stringify([{ contentType: 'image/png', sizeBytes: photo.size }]),
    })).json();
    await fetch(slot.url, { method: 'PUT', headers: slot.headers, body: photo });
    const created = await (await fetch('/api/memories', {
      method: 'POST', headers, body: JSON.stringify({
        caption: text, visibility: 'ONLY_ME', uploadIds: [slot.uploadId],
        location: { name: 'Reykjavik', latitude: 64.1466, longitude: -21.9426 },
      }),
    })).json();
    return created.id as string;
  }, caption);
  await page.goto(`/m/${memoryId}`);
  await expect(page.getByRole('button', { name: 'Like' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Main mobile' }).getByRole('link', { name: 'Map' }).click();
  const marker = page.locator('.leaflet-marker-icon[title^="Reykjavik"]');
  await expect(marker).toBeVisible();
  await marker.click();
  await expect(page).toHaveURL(new RegExp(`/m/${memoryId}$`));
  await expect(page.getByText(caption)).toBeVisible();
});
