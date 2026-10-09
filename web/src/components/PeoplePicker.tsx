import { type KeyboardEvent, useMemo, useState } from 'react';
import { useMe, useUserList } from '../api/queries';
import type { UserSummary } from '../api/types';
import { inputClass } from './ui';

/**
 * Tag people who were there. Suggestions come from the people you follow and who follow you (Phase 1 has no
 * search endpoint); any other username can still be typed and is checked by the server.
 */
export function PeoplePicker({ value, onChange }: { value: string[]; onChange: (usernames: string[]) => void }) {
  const me = useMe();
  const username = me.data?.username ?? '';
  const following = useUserList(username, 'following');
  const followers = useUserList(username, 'followers');
  const [query, setQuery] = useState('');

  const known = useMemo(() => {
    const byName = new Map<string, UserSummary>();
    [following.data, followers.data].forEach((data) =>
      data?.pages.forEach((page) => page.items.forEach((user) => byName.set(user.username, user))),
    );
    return [...byName.values()];
  }, [following.data, followers.data]);

  const needle = query.trim().replace(/^@/, '').toLowerCase();
  const suggestions = needle
    ? known
        .filter((user) => !value.includes(user.username))
        .filter((user) => user.username.includes(needle) || user.displayName.toLowerCase().includes(needle))
        .slice(0, 5)
    : [];

  const add = (name: string) => {
    const clean = name.trim().replace(/^@/, '').toLowerCase();
    if (clean && !value.includes(clean)) onChange([...value, clean]);
    setQuery('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      add(suggestions[0]?.username ?? query);
    }
  };

  return (
    <div className="space-y-2">
      <span className="text-sm font-medium text-stone-700 dark:text-stone-300">Who was there?</span>
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {value.map((name) => (
            <li key={name} className="flex items-center gap-1 rounded-full bg-stone-100 px-3 py-1 text-sm dark:bg-stone-800">
              @{name}
              <button
                type="button"
                aria-label={`Remove @${name}`}
                onClick={() => onChange(value.filter((other) => other !== name))}
                className="text-stone-500"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="relative">
        <input
          aria-label="Tag people"
          placeholder="@username"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={onKeyDown}
          className={inputClass}
        />
        {suggestions.length > 0 && (
          <ul role="listbox" className="absolute z-10 mt-1 w-full rounded-lg border border-stone-200 bg-white shadow dark:border-stone-700 dark:bg-stone-900">
            {suggestions.map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  onClick={() => add(user.username)}
                  className="w-full px-3 py-2 text-left text-sm hover:bg-stone-100 dark:hover:bg-stone-800"
                >
                  <span className="font-semibold">{user.username}</span>{' '}
                  <span className="text-stone-500">{user.displayName}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
