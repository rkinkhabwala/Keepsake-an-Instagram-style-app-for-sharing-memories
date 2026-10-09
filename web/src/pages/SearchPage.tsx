import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSearch } from '../api/queries';
import { Avatar } from '../components/Avatar';
import { ErrorNotice, Spinner, inputClass } from '../components/ui';
import { hashtagPath } from '../lib/linkify';

/** Finds people (by username or display name) and hashtags, as you type. Start with @ or # to narrow it. */
export function SearchPage() {
  const [text, setText] = useState('');
  const [query, setQuery] = useState('');

  // Wait for a pause in typing rather than searching on every keystroke.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(text.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [text]);

  const results = useSearch(query);
  const users = results.data?.users ?? [];
  const hashtags = results.data?.hashtags ?? [];

  return (
    <div className="mx-auto max-w-[600px] space-y-4 px-4 py-6">
      <input
        type="search"
        aria-label="Search people and hashtags"
        placeholder="Search people or #hashtags"
        autoFocus
        value={text}
        onChange={(event) => setText(event.target.value)}
        className={inputClass}
      />
      {query === '' ? null : results.isPending ? (
        <Spinner label="Searching" />
      ) : results.isError ? (
        <ErrorNotice error={results.error} />
      ) : users.length === 0 && hashtags.length === 0 ? (
        <p className="text-sm text-stone-500 dark:text-stone-400">No results for “{query}”.</p>
      ) : (
        <>
          {users.length > 0 && (
            <section aria-label="People" className="space-y-3">
              <h2 className="text-sm font-semibold text-stone-500 dark:text-stone-400">People</h2>
              <ul className="space-y-3">
                {users.map((user) => (
                  <li key={user.id}>
                    <Link to={`/u/${user.username}`} className="flex items-center gap-3">
                      <Avatar user={user} />
                      <span>
                        <span className="block text-sm font-semibold">{user.username}</span>
                        <span className="block text-sm text-stone-500">{user.displayName}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {hashtags.length > 0 && (
            <section aria-label="Hashtags" className="space-y-2">
              <h2 className="text-sm font-semibold text-stone-500 dark:text-stone-400">Hashtags</h2>
              <ul className="flex flex-wrap gap-2">
                {hashtags.map((tag) => (
                  <li key={tag}>
                    <Link to={hashtagPath(tag)} className="rounded-full bg-stone-100 px-3 py-1 text-sm dark:bg-stone-800">
                      #{tag}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}
