import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useMarkNotificationsRead, useNotifications } from '../api/queries';
import type { AppNotification } from '../api/types';
import { Avatar } from '../components/Avatar';
import { ErrorNotice, LoadMore, Spinner } from '../components/ui';

export function notificationText(notification: AppNotification): string {
  const actor = notification.actor?.username ?? 'Someone';
  switch (notification.type) {
    case 'LIKE': {
      const others = notification.othersCount;
      if (others === 0) return `${actor} liked your memory`;
      return `${actor} and ${others} ${others === 1 ? 'other' : 'others'} liked your memory`;
    }
    case 'COMMENT':
      return notification.commentPreview ? `${actor} commented: “${notification.commentPreview}”` : `${actor} commented`;
    case 'FOLLOW':
      return `${actor} started following you`;
    case 'FOLLOW_REQUEST':
      return `${actor} asked to follow you`;
    case 'TAGGED':
      return `${actor} tagged you in a memory`;
    case 'ON_THIS_DAY':
      return 'You have memories from this day in earlier years';
  }
}

function target(notification: AppNotification): string {
  switch (notification.type) {
    case 'FOLLOW':
      return `/u/${notification.actor?.username ?? ''}`;
    case 'FOLLOW_REQUEST':
      return '/settings';
    case 'ON_THIS_DAY':
      return '/on-this-day';
    default:
      return notification.memoryId ? `/m/${notification.memoryId}` : '/';
  }
}

function timeAgo(at: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(at).getTime()) / 60000));
  if (minutes < 1) return 'now';
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

/** Spec §9 screen 10. Opening the page marks everything read; unread items stay highlighted until you leave. */
export function NotificationsPage() {
  const notifications = useNotifications();
  const markRead = useMarkNotificationsRead();
  const items = notifications.data?.pages.flatMap((page) => page.items) ?? [];
  const hasUnread = items.some((item) => !item.read);
  const { mutate, isPending } = markRead;

  useEffect(() => {
    if (hasUnread && !isPending) mutate();
  }, [hasUnread, isPending, mutate]);

  return (
    <section className="mx-auto max-w-[470px] px-4 py-4" aria-labelledby="notifications-heading">
      <h1 id="notifications-heading" className="mb-3 text-xl font-semibold">
        Notifications
      </h1>
      {notifications.isPending && <Spinner />}
      {notifications.isError && <ErrorNotice error={notifications.error} />}
      {notifications.isSuccess && items.length === 0 && (
        <p className="text-stone-600 dark:text-stone-400">Nothing yet. Likes, comments, and follows will show up here.</p>
      )}
      <ul aria-label="Notifications" className="divide-y divide-stone-200 dark:divide-stone-800">
        {items.map((item) => (
          <li key={item.id}>
            <Link
              to={target(item)}
              className={`flex items-center gap-3 py-3 ${item.read ? '' : 'font-medium'}`}
              aria-label={`${notificationText(item)}${item.read ? '' : ' (new)'}`}
            >
              {item.actor ? <Avatar user={item.actor} size="sm" /> : <span aria-hidden className="w-8 text-center text-xl">✦</span>}
              <span className="min-w-0 flex-1 text-sm">
                {notificationText(item)}{' '}
                <span className="text-stone-500 dark:text-stone-400">{timeAgo(item.at)}</span>
              </span>
              {!item.read && <span aria-hidden className="h-2 w-2 rounded-full bg-emerald-600" />}
              {item.memoryThumbUrl && <img src={item.memoryThumbUrl} alt="" className="h-10 w-10 rounded object-cover" />}
            </Link>
          </li>
        ))}
      </ul>
      <LoadMore onVisible={() => void notifications.fetchNextPage()} enabled={!!notifications.hasNextPage && !notifications.isFetchingNextPage} />
    </section>
  );
}
