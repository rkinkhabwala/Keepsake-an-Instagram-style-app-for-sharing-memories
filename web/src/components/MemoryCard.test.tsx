import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, memory, renderApp, signedIn } from '../test/utils';
import { MemoryCard } from './MemoryCard';

describe('MemoryCard', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows when and where it happened, the badge, and the latest comments', () => {
    const api = fakeApi();
    signedIn(api);
    renderApp(
      <MemoryCard
        memory={memory({
          visibility: 'CLOSE_FRIENDS',
          commentCount: 3,
          latestComments: [
            { id: 'c1', parentId: null, author: { id: 'u2', username: 'cy', displayName: 'Cy', avatarUrl: null }, body: 'So pretty', deleted: false, canDelete: false, createdAt: '2026-01-01T00:00:00Z', replies: [] },
          ],
        })}
      />,
    );

    expect(screen.getByText('Happened on July 4, 2019')).toBeInTheDocument();
    expect(screen.getByText('Santa Monica')).toBeInTheDocument();
    expect(screen.getByText('Close friends')).toBeInTheDocument();
    expect(screen.getByText('So pretty')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View all 3 comments' })).toHaveAttribute('href', '/m/m-1#comments');
  });

  it('truncates long captions until "more" is pressed', async () => {
    const api = fakeApi();
    signedIn(api);
    const caption = `${'a'.repeat(130)} END`;
    renderApp(<MemoryCard memory={memory({ caption })} />);

    expect(screen.queryByText(/END/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'more' }));
    expect(screen.getByText(/END/)).toBeInTheDocument();
  });

  it('likes on double tap of the photos, and never un-likes that way', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('PUT', '/api/memories/m-1/like', null, 204);
    api.on('GET', '/api/memories/m-1', memory({ likedByViewer: true, likeCount: 3 }));
    renderApp(<MemoryCard memory={memory()} />);

    const photos = screen.getByTestId('memory-photos');
    fireEvent.click(photos);
    fireEvent.click(photos);

    await waitFor(() => expect(api.calls.some((call) => call.method === 'PUT' && call.path === '/api/memories/m-1/like')).toBe(true));
  });

  it('toggles like and save from the buttons', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('PUT', '/api/memories/m-1/like', null, 204);
    api.on('PUT', '/api/memories/m-1/save', null, 204);
    api.on('GET', '/api/memories/m-1', memory());
    renderApp(<MemoryCard memory={memory()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Like' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const writes = api.calls.filter((call) => call.method === 'PUT').map((call) => call.path);
    expect(writes).toEqual(['/api/memories/m-1/like', '/api/memories/m-1/save']);
  });
});
