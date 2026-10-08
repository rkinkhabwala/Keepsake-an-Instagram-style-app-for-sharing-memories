import type { UserSummary } from '../api/types';

const SIZES = { sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-20 w-20 text-2xl sm:h-28 sm:w-28' };

export function Avatar({ user, size = 'md' }: { user: Pick<UserSummary, 'displayName' | 'avatarUrl'>; size?: keyof typeof SIZES }) {
  if (user.avatarUrl) {
    return <img src={user.avatarUrl} alt="" className={`${SIZES[size]} shrink-0 rounded-full object-cover`} />;
  }
  return (
    <span
      aria-hidden
      className={`${SIZES[size]} flex shrink-0 items-center justify-center rounded-full bg-emerald-100 font-semibold text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200`}
    >
      {user.displayName.trim().charAt(0).toUpperCase() || '?'}
    </span>
  );
}
