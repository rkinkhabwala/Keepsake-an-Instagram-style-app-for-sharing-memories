import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AppNotification } from '../api/types';
import { NotificationBell } from '../components/Layout';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { NotificationsPage, notificationText } from './NotificationsPage';

function notification(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: 'n-1',
    type: 'LIKE',
    actor: { id: 'u-bo', username: 'bo', displayName: 'Bo', avatarUrl: null },
    othersCount: 0,
    memoryId: 'm-1',
    memoryThumbUrl: '/t.webp',
    commentPreview: null,
    read: false,
    at: new Date().toISOString(),
    ...overrides,
  };
}

describe('notifications', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('describes each kind in plain language, grouping likes', () => {
    expect(notificationText(notification({ othersCount: 0 }))).toBe('bo liked your memory');
    expect(notificationText(notification({ othersCount: 1 }))).toBe('bo and 1 other liked your memory');
    expect(notificationText(notification({ othersCount: 4 }))).toBe('bo and 4 others liked your memory');
    expect(notificationText(notification({ type: 'COMMENT', commentPreview: 'So good' }))).toBe('bo commented: “So good”');
    expect(notificationText(notification({ type: 'FOLLOW' }))).toBe('bo started following you');
    expect(notificationText(notification({ type: 'FOLLOW_REQUEST' }))).toBe('bo asked to follow you');
    expect(notificationText(notification({ type: 'TAGGED' }))).toBe('bo tagged you in a memory');
    expect(notificationText(notification({ type: 'ON_THIS_DAY', actor: null }))).toBe(
      'You have memories from this day in earlier years',
    );
  });

  it('lists notifications with links and marks them read', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/notifications(\?.*)?$/, {
      items: [
        notification({ id: 'n-2', othersCount: 2 }),
        notification({ id: 'n-1', type: 'FOLLOW', memoryId: null, memoryThumbUrl: null, read: true }),
      ],
      nextCursor: null,
    });
    api.on('POST', '/api/notifications/read', null, 204);
    renderApp(<NotificationsPage />, { route: '/notifications', path: '/notifications' });

    expect(await screen.findByRole('link', { name: 'bo and 2 others liked your memory (new)' })).toHaveAttribute('href', '/m/m-1');
    expect(screen.getByRole('link', { name: 'bo started following you' })).toHaveAttribute('href', '/u/bo');
    await waitFor(() => expect(api.calls.some((call) => call.method === 'POST' && call.path === '/api/notifications/read')).toBe(true));
  });

  it('shows the unread count on the bell', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/notifications/unread-count', { count: 3 });
    renderApp(<NotificationBell />);

    expect(await screen.findByRole('link', { name: 'Notifications, 3 unread' })).toHaveAttribute('href', '/notifications');
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
