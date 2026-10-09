import { Link } from 'react-router-dom';
import type { GridItem } from '../api/types';
import { BlurhashImage } from './BlurhashImage';
import { LoadMore } from './ui';

export function MemoryGrid({
  items,
  hasMore,
  onLoadMore,
  empty,
}: {
  items: GridItem[];
  hasMore: boolean;
  onLoadMore: () => void;
  empty: string;
}) {
  if (items.length === 0) {
    return <p className="py-12 text-center text-sm text-stone-500 dark:text-stone-400">{empty}</p>;
  }
  return (
    <>
      <ul className="grid grid-cols-3 gap-0.5 sm:gap-1">
        {items.map((item) => (
          <li key={item.id} className="relative">
            <Link to={`/m/${item.id}`} aria-label={`Memory from ${item.occurredOn}`}>
              <BlurhashImage src={item.thumbUrl} blurhash={item.blurhash} alt="" aspectRatio={1} />
              {item.video && (
                <span aria-hidden className="absolute bottom-1 left-1 text-xs text-white drop-shadow">▶</span>
              )}
              {item.mediaCount > 1 && (
                <span aria-hidden className="absolute right-1 top-1 text-xs text-white drop-shadow">
                  ❐
                </span>
              )}
            </Link>
          </li>
        ))}
      </ul>
      <LoadMore onVisible={onLoadMore} enabled={hasMore} />
    </>
  );
}
