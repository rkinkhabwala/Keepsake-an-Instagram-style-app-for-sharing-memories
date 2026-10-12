import { useRef, useState } from 'react';
import type { MemoryMedia } from '../api/types';
import { BlurhashImage } from './BlurhashImage';

/** Swipeable photo carousel built on CSS scroll snapping, with buttons and dots for non-touch use. */
export function Carousel({ media, label }: { media: MemoryMedia[]; label: string }) {
  const track = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);
  const first = media[0];
  // Every slide shares the first photo's shape, capped between 4:5 portrait and 1.91:1 landscape.
  const ratio = first?.width && first?.height ? Math.min(1.91, Math.max(0.8, first.width / first.height)) : 1;

  const go = (next: number) => {
    const node = track.current;
    if (!node) return;
    node.scrollTo({ left: next * node.clientWidth, behavior: 'smooth' });
  };

  return (
    <div className="relative" aria-roledescription="carousel" aria-label={label}>
      <div
        ref={track}
        onScroll={(event) => {
          const node = event.currentTarget;
          setIndex(Math.round(node.scrollLeft / Math.max(1, node.clientWidth)));
        }}
        className="flex snap-x snap-mandatory overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {media.map((item, position) => (
          <div key={item.position} className="w-full shrink-0 snap-center" aria-label={`Photo ${position + 1} of ${media.length}`}>
            {item.type === 'VIDEO' ? (
              <video
                src={item.displayUrl ?? undefined}
                poster={item.posterUrl ?? undefined}
                controls
                muted
                playsInline
                preload="metadata"
                aria-label={`Video, ${Math.round((item.durationMs ?? 0) / 1000)} seconds`}
                className="w-full bg-black object-contain"
                style={{ aspectRatio: String(ratio) }}
              />
            ) : (
              <BlurhashImage src={item.displayUrl} blurhash={item.blurhash} alt="" aspectRatio={ratio} />
            )}
          </div>
        ))}
      </div>
      {media.length > 1 && (
        <>
          {index > 0 && (
            <button
              type="button"
              aria-label="Previous photo"
              onClick={() => go(index - 1)}
              className="absolute left-2 top-1/2 hidden h-8 w-8 -translate-y-1/2 rounded-full bg-white/80 text-stone-900 shadow sm:block"
            >
              ‹
            </button>
          )}
          {index < media.length - 1 && (
            <button
              type="button"
              aria-label="Next photo"
              onClick={() => go(index + 1)}
              className="absolute right-2 top-1/2 hidden h-8 w-8 -translate-y-1/2 rounded-full bg-white/80 text-stone-900 shadow sm:block"
            >
              ›
            </button>
          )}
          <div className="absolute bottom-2 flex w-full justify-center gap-1" aria-hidden>
            {media.map((item, position) => (
              <span
                key={item.position}
                className={`h-1.5 w-1.5 rounded-full ${position === index ? 'bg-white' : 'bg-white/50'}`}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
