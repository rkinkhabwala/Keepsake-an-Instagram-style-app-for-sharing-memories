import { expect, test } from '@playwright/test';
import { createMemory, newUser, resetRateLimits, uniqueName, waitForStack } from './helpers';

test.beforeAll(({ baseURL }) => waitForStack(baseURL!));
test.beforeEach(() => resetRateLimits());

test('a collaborator adds their memories; album viewers only see what they could see anyway', async ({ browser }) => {
  const owner = await newUser(browser, 'e2e_alb_own');
  const editor = await newUser(browser, 'e2e_alb_ed');
  const viewer = await newUser(browser, 'e2e_alb_view');
  for (const user of [editor, viewer]) {
    await user.page.goto(`/u/${owner.username}`);
    await user.page.getByRole('button', { name: 'Follow', exact: true }).click();
    await expect(user.page.getByRole('button', { name: 'Following' })).toBeVisible();
  }

  // The owner creates a followers-only album and adds the editor.
  const title = `Trip ${uniqueName('a')}`;
  await owner.page.goto(`/u/${owner.username}`);
  await owner.page.getByRole('tab', { name: 'Albums' }).click();
  await owner.page.getByRole('button', { name: 'New album' }).click();
  await owner.page.getByLabel('Album title').fill(title);
  await owner.page.getByRole('button', { name: 'Create album' }).click();
  await expect(owner.page.getByRole('heading', { name: title })).toBeVisible();
  const albumUrl = owner.page.url();
  await owner.page.getByRole('button', { name: 'Collaborators' }).click();
  const people = owner.page.getByRole('region', { name: 'Manage collaborators' });
  await people.getByRole('listitem').filter({ hasText: editor.username }).getByRole('button', { name: 'Add as editor' }).click();
  await expect(owner.page.getByRole('list', { name: 'Collaborators' })).toContainText(editor.username);

  // The editor adds two of their own memories: one public, one only for themselves.
  const shared = `Shared ${uniqueName('c')}`;
  const privateOne = `Mine only ${uniqueName('c')}`;
  await createMemory(editor.page, shared, /^Public/);
  await createMemory(editor.page, privateOne, /^Only me/);
  await editor.page.goto(albumUrl);
  await editor.page.getByRole('button', { name: 'Add memories' }).click();
  const picker = editor.page.getByRole('region', { name: 'Add memories' });
  const choices = picker.getByRole('checkbox');
  await expect(choices).toHaveCount(2);
  for (const choice of await choices.all()) await choice.check({ force: true });
  await editor.page.getByRole('button', { name: 'Add 2' }).click();
  await expect(editor.page.getByText('2 memories')).toBeVisible();

  // The viewer follows the owner but not the editor: they see the public memory only.
  await viewer.page.goto(albumUrl);
  await expect(viewer.page.getByRole('heading', { name: title })).toBeVisible();
  await expect(viewer.page.getByText(/^By @.* · 1 memory/)).toBeVisible();
  const tiles = viewer.page.getByRole('link', { name: /^Memory from / });
  await expect(tiles).toHaveCount(1);
  await tiles.first().click();
  await expect(viewer.page.getByText(shared)).toBeVisible();
});
