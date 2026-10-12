import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { getAccessToken, refreshSession } from '../api/client';
import { keys } from '../api/queries';

/**
 * Keeps a STOMP connection to /ws while signed in. Each pushed notification refreshes the notification list and
 * the bell's unread count. The server checks the access token only on CONNECT, so a reconnect after it expired
 * is refused once; the client then refreshes the session and the next attempt succeeds.
 */
export function useLiveNotifications(enabled: boolean) {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (!enabled || import.meta.env.MODE === 'test') return;
    const refresh = () => queryClient.invalidateQueries({ queryKey: keys.notifications });
    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      beforeConnect: async () => {
        const token = getAccessToken() ?? (await refreshSession())?.accessToken ?? '';
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        client.subscribe('/user/queue/notifications', refresh);
        // Catch up on anything that arrived while disconnected.
        void refresh();
      },
      onStompError: () => void refreshSession(),
    });
    client.activate();
    return () => void client.deactivate();
  }, [enabled, queryClient]);
}
