import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Album, AlbumRole } from '../api/types';
import { ME, fakeApi, renderApp, signedIn } from '../test/utils';
import { AlbumPage } from './AlbumPage';

function album(viewerRole: AlbumRole): Album {
  return {
    id: 'al1', title: 'Lisbon trip', description: 'Spring 2024', visibility: 'FOLLOWERS',
    owner: { id: 'u-bo', username: 'bo', displayName: 'Bo', avatarUrl: null }, viewerRole,
    collaborators: [{ user: { id: 'u-me', username: 'ana', displayName: 'Ana', avatarUrl: null }, role: 'EDITOR' }],
    coverThumbUrl: null, coverBlurhash: null, memoryCount: 1, createdAt: '2026-01-01T00:00:00Z',
  };
}

function albumApi(role: AlbumRole) {
  const api = fakeApi();
  signedIn(api);
  api.on('GET', '/api/albums/al1', album(role));
  api.on('GET', /\/api\/albums\/al1\/memories/, {
    items: [{ id: 'm1', thumbUrl: '/t.webp', blurhash: null, mediaCount: 1, occurredOn: '2024-04-02' }], nextCursor: null,
  });
  return api;
}

describe('AlbumPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the album and only the controls the viewer may use', async () => {
    albumApi('NONE');
    renderApp(<AlbumPage />, { route: '/albums/al1', path: '/albums/:id' });

    expect(await screen.findByRole('heading', { name: 'Lisbon trip' })).toBeInTheDocument();
    expect(screen.getByText(/1 memory · Followers/)).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Memory from 2024-04-02' })).toBeInTheDocument();
    for (const name of ['Add memories', 'Collaborators', 'Edit', 'Leave album']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('lets an editor add their own memories and leave', async () => {
    const api = albumApi('EDITOR');
    api.on('GET', /\/api\/users\/ana\/memories/, {
      items: [{ id: 'mine1', thumbUrl: '/t.webp', blurhash: null, mediaCount: 1, occurredOn: '2024-04-03' }], nextCursor: null,
    });
    api.on('POST', '/api/albums/al1/memories', null, 204);
    api.on('DELETE', '/api/albums/al1/collaborators/me', null, 204);
    vi.stubGlobal('confirm', () => true);
    renderApp(<AlbumPage />, { route: '/albums/al1', path: '/albums/:id' });

    expect(await screen.findByRole('button', { name: 'Leave album' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add memories' }));
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Memory from 2024-04-03' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add 1' }));
    expect(api.calls.find((call) => call.method === 'POST' && call.path === '/api/albums/al1/memories')?.body)
      .toEqual({ memoryIds: ['mine1'] });

    await userEvent.click(screen.getByRole('button', { name: 'Leave album' }));
    expect(await screen.findByText('Navigated away')).toBeInTheDocument();
  });

  it('lets the owner add a follower as a collaborator', async () => {
    const api = albumApi('OWNER');
    api.on('GET', /\/api\/users\/bo\/followers/, {
      items: [{ id: 'u-cy', username: 'cyra', displayName: 'Cyra', avatarUrl: null }], nextCursor: null,
    });
    api.on('POST', '/api/albums/al1/collaborators', null, 204);
    renderApp(<AlbumPage />, { route: '/albums/al1', path: '/albums/:id' });

    await userEvent.click(await screen.findByRole('button', { name: 'Collaborators' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add as editor' }));

    expect(api.calls.find((call) => call.path === '/api/albums/al1/collaborators')?.body)
      .toEqual({ userId: 'u-cy', role: 'EDITOR' });
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('shows not-available for an album the viewer cannot see', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/albums/al1', { detail: 'Album not found' }, 404);
    renderApp(<AlbumPage />, { route: '/albums/al1', path: '/albums/:id' });

    expect(await screen.findByRole('heading', { name: 'This isn’t available' })).toBeInTheDocument();
  });
});

describe('Profile albums tab', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lists albums and creates a new one on your own profile', async () => {
    const { AlbumList } = await import('../components/AlbumList');
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/users/ana/albums', { items: [{ ...album('OWNER'), owner: { ...ME.user, avatarUrl: null } }], nextCursor: null });
    api.on('POST', '/api/albums', { ...album('OWNER'), id: 'al2' }, 201);
    renderApp(<AlbumList username="ana" own />, { route: '/u/ana', path: '/u/:username' });

    expect(await screen.findByRole('link', { name: /Lisbon trip/ })).toHaveAttribute('href', '/albums/al1');
    await userEvent.click(screen.getByRole('button', { name: 'New album' }));
    await userEvent.type(screen.getByLabelText('Album title'), 'Summer');
    await userEvent.click(screen.getByRole('button', { name: 'Create album' }));

    expect(api.calls.find((call) => call.method === 'POST' && call.path === '/api/albums')?.body)
      .toEqual({ title: 'Summer', description: '', visibility: 'FOLLOWERS' });
    expect(await screen.findByText('Navigated away')).toBeInTheDocument();
  });
});
