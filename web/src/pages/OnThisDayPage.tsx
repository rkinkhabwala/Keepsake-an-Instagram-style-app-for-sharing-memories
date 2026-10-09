import { useOnThisDay } from '../api/queries';
import { MemoryGrid } from '../components/MemoryGrid';
import { ErrorNotice, Spinner } from '../components/ui';
import { formatOccurredOn, todayIso } from '../lib/format';

export function yearsAgoLabel(yearsAgo: number): string {
  return yearsAgo === 1 ? '1 year ago' : `${yearsAgo} years ago`;
}

/** Your memories (and ones you were tagged in) from this day in earlier years. */
export function OnThisDayPage() {
  const today = todayIso();
  const onThisDay = useOnThisDay(today);

  return (
    <div className="mx-auto max-w-[935px] space-y-8 px-4 py-6">
      <header>
        <h1 className="text-xl font-semibold">On this day</h1>
        <p className="text-sm text-stone-600 dark:text-stone-400">{formatOccurredOn(today)}</p>
      </header>
      {onThisDay.isPending ? (
        <Spinner />
      ) : onThisDay.isError ? (
        <ErrorNotice error={onThisDay.error} />
      ) : onThisDay.data.years.length === 0 ? (
        <p className="py-12 text-center text-sm text-stone-500 dark:text-stone-400">
          Nothing from this day yet. Come back tomorrow.
        </p>
      ) : (
        onThisDay.data.years.map((year) => (
          <section key={year.year} aria-label={yearsAgoLabel(year.yearsAgo)} className="space-y-3">
            <h2 className="font-semibold">
              {yearsAgoLabel(year.yearsAgo)} <span className="font-normal text-stone-500">· {year.year}</span>
            </h2>
            <MemoryGrid items={year.memories} hasMore={false} onLoadMore={() => undefined} empty="" />
          </section>
        ))
      )}
    </div>
  );
}
