import { expect, test } from '@playwright/test';
import { createMemory, newUser, resetRateLimits, uniqueName, waitForStack } from './helpers';

test.beforeAll(({ baseURL }) => waitForStack(baseURL!));
test.beforeEach(() => resetRateLimits());

test('a like shows up on the author’s open page within 2 seconds', async ({ browser }) => {
  const author = await newUser(browser, 'e2e_notified');
  const fan = await newUser(browser, 'e2e_liker');
  await createMemory(author.page, `Notify me ${uniqueName('c')}`, /^Public/);

  // Open the feed and wait until the notification socket is connected.
  const socket = author.page.waitForEvent('websocket', (ws) => ws.url().endsWith('/ws'));
  await author.page.goto('/');
  await (await socket).waitForEvent('framereceived', (frame) => String(frame.payload).startsWith('CONNECTED'));
  const bell = author.page.getByRole('link', { name: /^Notifications/ });
  await expect(bell).toHaveAccessibleName('Notifications');

  await fan.page.goto(`/u/${author.username}`);
  await fan.page.getByRole('tabpanel').getByRole('link').first().click();
  const liked = fan.page.waitForResponse((response) => response.url().endsWith('/like') && response.ok());
  await fan.page.getByRole('button', { name: 'Like', exact: true }).click();
  await liked;

  await expect(bell).toHaveAccessibleName('Notifications, 1 unread', { timeout: 2000 });
  await bell.click();
  await expect(author.page.getByRole('link', { name: `${fan.username} liked your memory (new)` })).toBeVisible();
});
