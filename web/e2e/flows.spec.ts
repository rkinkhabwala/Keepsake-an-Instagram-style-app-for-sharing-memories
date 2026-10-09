import { expect, test } from '@playwright/test';
import { createMemory, newUser, resetRateLimits, uniqueName, waitForStack } from './helpers';

test.beforeAll(({ baseURL }) => waitForStack(baseURL!));
test.beforeEach(() => resetRateLimits());

test('signup → create memory → see it in own feed', async ({ browser }) => {
  const { page } = await newUser(browser, 'e2e_new');
  const caption = `First keepsake ${uniqueName('c')}`;

  await createMemory(page, caption, /^Public/);

  await page.getByRole('navigation', { name: 'Main mobile' }).getByRole('link', { name: 'Home' }).click();
  await expect(page.getByRole('list', { name: 'Feed' }).getByText(caption)).toBeVisible();
});

test("B follows private A → A approves → B sees A's FOLLOWERS memory", async ({ browser }) => {
  const a = await newUser(browser, 'e2e_priv');
  const b = await newUser(browser, 'e2e_fan');
  const caption = `Followers only ${uniqueName('c')}`;

  await a.page.goto('/settings');
  const privateToggle = a.page.getByLabel(/Private account/);
  await privateToggle.check();
  await expect(privateToggle).toBeChecked();
  await createMemory(a.page, caption, /^Followers/);

  await b.page.goto(`/u/${a.username}`);
  await b.page.getByRole('button', { name: 'Request to follow' }).click();
  await expect(b.page.getByRole('button', { name: 'Requested' })).toBeVisible();
  await expect(b.page.getByText('This account is private')).toBeVisible();

  await a.page.goto('/settings');
  const requests = a.page.getByRole('region', { name: 'Follow requests' });
  await expect(requests.getByText(b.username)).toBeVisible();
  await requests.getByRole('button', { name: 'Approve' }).click();
  await expect(requests.getByText('No pending requests.')).toBeVisible();

  await b.page.goto(`/u/${a.username}`);
  await expect(b.page.getByRole('button', { name: 'Following' })).toBeVisible();
  await b.page.getByRole('tabpanel').getByRole('link').first().click();
  await expect(b.page.getByText(caption)).toBeVisible();
  await b.page.goto('/');
  await expect(b.page.getByRole('list', { name: 'Feed' }).getByText(caption)).toBeVisible();
});

test('CLOSE_FRIENDS memory is hidden from a follower who is not a close friend', async ({ browser }) => {
  const author = await newUser(browser, 'e2e_author');
  const follower = await newUser(browser, 'e2e_follow');
  const friend = await newUser(browser, 'e2e_close');
  const caption = `Close friends only ${uniqueName('c')}`;

  for (const user of [follower, friend]) {
    await user.page.goto(`/u/${author.username}`);
    await user.page.getByRole('button', { name: 'Follow', exact: true }).click();
    await expect(user.page.getByRole('button', { name: 'Following' })).toBeVisible();
  }

  await author.page.goto('/settings');
  const closeFriend = author.page.getByLabel(`Close friend: ${friend.username}`);
  await closeFriend.check();
  await expect(closeFriend).toBeChecked();
  const memoryUrl = await createMemory(author.page, caption, /^Close friends/);

  await friend.page.goto('/');
  await expect(friend.page.getByRole('list', { name: 'Feed' }).getByText(caption)).toBeVisible();

  await follower.page.goto('/');
  await expect(follower.page.getByText('Nothing here yet')).toBeVisible();
  await expect(follower.page.getByText(caption)).toHaveCount(0);
  await follower.page.goto(memoryUrl);
  await expect(follower.page.getByRole('heading', { name: 'This isn’t available' })).toBeVisible();
  await follower.page.goto(`/u/${author.username}`);
  await expect(follower.page.getByText('No memories yet.')).toBeVisible();
});
