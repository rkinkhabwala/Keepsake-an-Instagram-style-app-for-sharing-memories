import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Profile } from '../api/types';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { ProfilePage } from './ProfilePage';

function profile(overrides: Partial<Profile>): Profile {
  return {
    id: 'u-bo', username: 'bo', displayName: 'Bo', bio: 'Hi', avatarUrl: null, isPrivate: false,
    memoryCount: 1, followerCount: 2, followingCount: 3, relationship: 'NONE', ...overrides,
  };
}

describe('ProfilePage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows a lock instead of the grid on a private account the viewer does not follow', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/users/bo', profile({ isPrivate: true, relationship: 'REQUESTED' }));
    renderApp(<ProfilePage />, { route: '/u/bo', path: '/u/:username' });

    expect(await screen.findByText('This account is private')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Requested' })).toBeInTheDocument();
    expect(api.calls.some((call) => call.path.startsWith('/api/users/bo/memories'))).toBe(false);
  });

  it('shows the grid with tabs, and Saved only on your own profile', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/users/ana', profile({ username: 'ana', relationship: 'SELF' }));
    api.on('GET', /\/api\/users\/ana\/memories/, { items: [{ id: 'm1', thumbUrl: '/t.webp', blurhash: null, mediaCount: 2, occurredOn: '2020-01-02', video: false }], nextCursor: null });
    api.on('GET', /\/api\/saved/, { items: [], nextCursor: null });
    renderApp(<ProfilePage />, { route: '/u/ana', path: '/u/:username' });

    expect(await screen.findByRole('link', { name: 'Memory from 2020-01-02' })).toHaveAttribute('href', '/m/m1');
    await userEvent.click(screen.getByRole('tab', { name: 'Saved' }));
    expect(await screen.findByText(/only for you/)).toBeInTheDocument();
  });

  it('blocks after confirmation and leaves the profile', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/users/bo', profile({}));
    api.on('GET', /\/api\/users\/bo\/memories/, { items: [], nextCursor: null });
    api.on('PUT', '/api/blocks/u-bo', null, 204);
    vi.stubGlobal('confirm', () => true);
    renderApp(<ProfilePage />, { route: '/u/bo', path: '/u/:username' });

    await userEvent.click(await screen.findByRole('button', { name: 'Block' }));

    expect(await screen.findByText('Navigated away')).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === 'PUT' && call.path === '/api/blocks/u-bo')).toBe(true);
  });

  it('follows a public account', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/users/bo', profile({}));
    api.on('GET', /\/api\/users\/bo\/memories/, { items: [], nextCursor: null });
    api.on('POST', '/api/users/bo/follow', { relationship: 'FOLLOWING' });
    renderApp(<ProfilePage />, { route: '/u/bo', path: '/u/:username' });

    await userEvent.click(await screen.findByRole('button', { name: 'Follow' }));

    expect(api.calls.some((call) => call.method === 'POST' && call.path === '/api/users/bo/follow')).toBe(true);
  });
});
