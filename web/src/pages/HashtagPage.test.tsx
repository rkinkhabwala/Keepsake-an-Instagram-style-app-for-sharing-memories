import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { HashtagPage } from './HashtagPage';

describe('HashtagPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the tag and its memories, and an empty state', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/hashtags\/summer\/memories/, {
      items: [{ id: 'm1', thumbUrl: '/t.webp', blurhash: null, mediaCount: 1, occurredOn: '2024-07-01', video: false }],
      nextCursor: null,
    });
    renderApp(<HashtagPage />, { route: '/tags/summer', path: '/tags/:tag' });

    expect(screen.getByRole('heading', { name: '#summer' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Memory from 2024-07-01' })).toHaveAttribute('href', '/m/m1');
  });

  it('says when nothing visible uses the tag', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/hashtags\/quiet\/memories/, { items: [], nextCursor: null });
    renderApp(<HashtagPage />, { route: '/tags/quiet', path: '/tags/:tag' });

    expect(await screen.findByText('No memories you can see use #quiet yet.')).toBeInTheDocument();
  });
});
