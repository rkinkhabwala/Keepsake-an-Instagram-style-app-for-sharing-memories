import { useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { useHashtagGrid } from '../api/queries';
import { MemoryGrid } from '../components/MemoryGrid';
import { ErrorNotice, Spinner } from '../components/ui';

/** Memories with a hashtag, limited by the server to those the viewer may see. */
export function HashtagPage() {
  const { tag = '' } = useParams();
  const grid = useHashtagGrid(tag);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = grid;
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="px-4 py-6">
      <h1 className="mb-4 text-xl font-semibold">#{tag}</h1>
      {grid.isPending ? (
        <Spinner />
      ) : grid.isError ? (
        <ErrorNotice error={grid.error} />
      ) : (
        <MemoryGrid
          items={grid.data.pages.flatMap((page) => page.items)}
          hasMore={!!hasNextPage}
          onLoadMore={loadMore}
          empty={`No memories you can see use #${tag} yet.`}
        />
      )}
    </div>
  );
}
