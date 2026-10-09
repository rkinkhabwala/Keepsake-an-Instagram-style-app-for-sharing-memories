import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useFeed, useOnThisDay } from '../api/queries';
import { MemoryCard } from '../components/MemoryCard';
import { ErrorNotice, LoadMore, Spinner } from '../components/ui';
import { todayIso } from '../lib/format';

/** A nudge at the top of the feed when there is something to look back on today. */
function OnThisDayCard() {
  const onThisDay = useOnThisDay(todayIso());
  const years = onThisDay.data?.years ?? [];
  if (years.length === 0) return null;
  const count = years.reduce((total, year) => total + year.memories.length, 0);
  return (
    <Link
      to="/on-this-day"
      className="mx-3 block rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-900 dark:bg-emerald-950 dark:text-emerald-100 sm:mx-0"
    >
      <span className="font-semibold">On this day</span> · {count === 1 ? '1 memory' : `${count} memories`} from
      earlier years →
    </Link>
  );
}

export function FeedPage() {
  const feed = useFeed();
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = feed;
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (feed.isPending) return <Spinner label="Loading your feed" />;
  if (feed.isError) return <div className="p-4"><ErrorNotice error={feed.error} /></div>;

  const memories = feed.data.pages.flatMap((page) => page.items);
  return (
    <div className="mx-auto max-w-[470px] space-y-4 pt-3 sm:pt-4">
      <OnThisDayCard />
      {memories.length === 0 ? (
        <div className="px-6 py-16 text-center">
          <h1 className="text-lg font-semibold">Nothing here yet</h1>
          <p className="mt-2 text-sm text-stone-600 dark:text-stone-400">
            Follow friends, or <Link to="/new" className="font-semibold text-emerald-700 dark:text-emerald-400">keep your first memory</Link>.
          </p>
        </div>
      ) : (
        <ul className="space-y-4" aria-label="Feed">
          {memories.map((memory) => (
            <li key={memory.id}>
              <MemoryCard memory={memory} />
            </li>
          ))}
        </ul>
      )}
      {isFetchingNextPage && <Spinner label="Loading more" />}
      <LoadMore onVisible={loadMore} enabled={!!hasNextPage} />
    </div>
  );
}
