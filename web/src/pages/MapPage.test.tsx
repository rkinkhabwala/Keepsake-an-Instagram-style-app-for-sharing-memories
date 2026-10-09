import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { MapPage } from './MapPage';

describe('MapPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('loads memories for the visible area and says when results are capped', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/memories\/map\?bbox=/, {
      items: [{ id: 'm1', latitude: 48.85, longitude: 2.35, locationName: 'Paris <b>', occurredOn: '2020-05-01',
        authorUsername: 'ana', thumbUrl: '/t.webp', blurhash: null }],
      truncated: true,
    });
    renderApp(<MapPage />, { route: '/map', path: '/map' });

    expect(screen.getByRole('region', { name: 'Map of memories' })).toBeInTheDocument();
    expect(await screen.findByText(/Showing the latest 500/)).toBeInTheDocument();
    const request = api.calls.find((call) => call.path.startsWith('/api/memories/map'));
    expect(decodeURIComponent(request!.path)).toMatch(/bbox=-?\d+(\.\d+)?(,-?\d+(\.\d+)?){3}$/);
    await waitFor(() => expect(document.querySelector('b')).toBeNull());
  });
});
