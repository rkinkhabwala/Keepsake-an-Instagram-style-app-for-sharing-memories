import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { SearchPage } from './SearchPage';

describe('SearchPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('searches after a pause and links people and hashtags', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/search\?q=mil/, {
      users: [{ id: 'u1', username: 'mila', displayName: 'Mila Lopez', avatarUrl: null }],
      hashtags: ['milan'],
    });
    renderApp(<SearchPage />, { route: '/search', path: '/search' });

    await userEvent.type(screen.getByLabelText('Search people and hashtags'), 'mil');

    expect(await screen.findByRole('link', { name: 'mila Mila Lopez' })).toHaveAttribute('href', '/u/mila');
    expect(screen.getByRole('link', { name: '#milan' })).toHaveAttribute('href', '/tags/milan');
    expect(api.calls.filter((call) => call.path.startsWith('/api/search'))).toHaveLength(1);
  });

  it('says when nothing matches', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/search/, { users: [], hashtags: [] });
    renderApp(<SearchPage />, { route: '/search', path: '/search' });

    await userEvent.type(screen.getByLabelText('Search people and hashtags'), '#nothing');

    expect(await screen.findByText('No results for “#nothing”.')).toBeInTheDocument();
  });
});
