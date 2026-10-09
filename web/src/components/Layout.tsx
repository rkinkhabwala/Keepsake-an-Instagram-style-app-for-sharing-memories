import { NavLink, Outlet } from 'react-router-dom';
import { useUnreadNotifications } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { useLiveNotifications } from '../lib/liveNotifications';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex flex-col items-center gap-0.5 rounded-lg px-3 py-1 text-xs ${
    isActive ? 'font-semibold text-emerald-700 dark:text-emerald-400' : 'text-stone-600 dark:text-stone-400'
  }`;

/** The bell (spec §9 screen 10), with the unread count kept current by pushed notifications. */
export function NotificationBell() {
  const unread = useUnreadNotifications().data?.count ?? 0;
  const label = unread > 0 ? `Notifications, ${unread} unread` : 'Notifications';
  return (
    <NavLink
      to="/notifications"
      aria-label={label}
      className="relative rounded-full p-1.5 text-xl leading-none text-stone-700 dark:text-stone-300"
    >
      <span aria-hidden>🔔︎</span>
      {unread > 0 && (
        <span
          aria-hidden
          className="absolute -right-1 -top-1 min-w-5 rounded-full bg-red-600 px-1 text-center text-xs font-semibold text-white"
        >
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </NavLink>
  );
}

/** Mobile-first shell: a top bar, the page, and a bottom tab bar (top-right links on wider screens). */
export function Layout() {
  const { user, status } = useAuth();
  useLiveNotifications(status === 'authenticated');
  const nav = (
    <>
      <NavLink to="/" end className={linkClass}>
        <span aria-hidden className="text-lg">⌂</span>Home
      </NavLink>
      <NavLink to="/search" className={linkClass}>
        <span aria-hidden className="text-lg">⌕</span>Search
      </NavLink>
      <NavLink to="/map" className={linkClass}>
        <span aria-hidden className="text-lg">⌖</span>Map
      </NavLink>
      <NavLink to="/new" className={linkClass}>
        <span aria-hidden className="text-lg">＋</span>New
      </NavLink>
      <NavLink to={`/u/${user?.username ?? ''}`} className={linkClass}>
        <span aria-hidden className="text-lg">◯</span>Profile
      </NavLink>
      <NavLink to="/settings" className={linkClass}>
        <span aria-hidden className="text-lg">⚙</span>Settings
      </NavLink>
    </>
  );
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <header className="sticky top-0 z-20 border-b border-stone-200 bg-stone-50/90 backdrop-blur dark:border-stone-800 dark:bg-stone-950/90">
        <div className="mx-auto flex max-w-[935px] items-center justify-between px-4 py-2">
          <NavLink to="/" className="text-lg font-semibold tracking-tight text-emerald-800 dark:text-emerald-300">
            Keepsake
          </NavLink>
          <div className="flex items-center gap-2">
            <nav aria-label="Main" className="hidden gap-1 sm:flex">
              {nav}
            </nav>
            <NotificationBell />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-[935px] pb-20 sm:pb-8">
        <Outlet />
      </main>
      <nav
        aria-label="Main mobile"
        className="fixed inset-x-0 bottom-0 z-20 flex justify-around border-t border-stone-200 bg-stone-50 py-1 dark:border-stone-800 dark:bg-stone-950 sm:hidden"
      >
        {nav}
      </nav>
    </div>
  );
}
