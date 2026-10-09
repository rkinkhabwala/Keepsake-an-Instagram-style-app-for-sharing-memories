import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex flex-col items-center gap-0.5 rounded-lg px-3 py-1 text-xs ${
    isActive ? 'font-semibold text-emerald-700 dark:text-emerald-400' : 'text-stone-600 dark:text-stone-400'
  }`;

/** Mobile-first shell: a top bar, the page, and a bottom tab bar (top-right links on wider screens). */
export function Layout() {
  const { user } = useAuth();
  const nav = (
    <>
      <NavLink to="/" end className={linkClass}>
        <span aria-hidden className="text-lg">⌂</span>Home
      </NavLink>
      <NavLink to="/search" className={linkClass}>
        <span aria-hidden className="text-lg">⌕</span>Search
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
          <nav aria-label="Main" className="hidden gap-1 sm:flex">
            {nav}
          </nav>
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
