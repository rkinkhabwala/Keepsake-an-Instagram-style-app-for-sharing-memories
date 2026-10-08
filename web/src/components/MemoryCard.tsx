import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useLike, useSave } from '../api/queries';
import type { Memory } from '../api/types';
import { formatOccurredOn, plural } from '../lib/format';
import { linkify } from '../lib/linkify';
import { Avatar } from './Avatar';
import { Carousel } from './Carousel';

const CAPTION_PREVIEW = 125;

export const VISIBILITY_BADGES: Record<Memory['visibility'], string | null> = {
  PUBLIC: null,
  FOLLOWERS: 'Followers',
  CLOSE_FRIENDS: 'Close friends',
  ONLY_ME: 'Only me',
};

export function LocationChip({ memory }: { memory: Memory }) {
  if (!memory.location) return null;
  const { name, latitude, longitude } = memory.location;
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-700 dark:bg-stone-800 dark:text-stone-300">
      <span aria-hidden>📍</span>
      {name ?? `${latitude?.toFixed(3)}, ${longitude?.toFixed(3)}`}
    </span>
  );
}

export function EngagementBar({ memory }: { memory: Memory }) {
  const like = useLike();
  const save = useSave();
  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        aria-pressed={memory.likedByViewer}
        aria-label={memory.likedByViewer ? 'Unlike' : 'Like'}
        onClick={() => like.mutate({ id: memory.id, on: !memory.likedByViewer })}
        className="rounded-full p-2 text-2xl leading-none hover:bg-stone-100 dark:hover:bg-stone-800"
      >
        <span aria-hidden className={memory.likedByViewer ? 'text-red-500' : ''}>
          {memory.likedByViewer ? '♥' : '♡'}
        </span>
      </button>
      <Link
        to={`/m/${memory.id}#comments`}
        aria-label="Comments"
        className="rounded-full p-2 text-xl leading-none hover:bg-stone-100 dark:hover:bg-stone-800"
      >
        <span aria-hidden>💬</span>
      </Link>
      <button
        type="button"
        aria-pressed={memory.savedByViewer}
        aria-label={memory.savedByViewer ? 'Remove from saved' : 'Save'}
        onClick={() => save.mutate({ id: memory.id, on: !memory.savedByViewer })}
        className="ml-auto rounded-full p-2 text-xl leading-none hover:bg-stone-100 dark:hover:bg-stone-800"
      >
        <span aria-hidden>{memory.savedByViewer ? '🔖' : '📑'}</span>
      </button>
    </div>
  );
}

/** A feed post: author, photos, when and where it happened, engagement, caption, and the latest two comments. */
export function MemoryCard({ memory }: { memory: Memory }) {
  const like = useLike();
  const [expanded, setExpanded] = useState(false);
  const [burst, setBurst] = useState(false);
  const lastTap = useRef(0);
  const caption = memory.caption ?? '';
  const truncated = !expanded && caption.length > CAPTION_PREVIEW;

  // Double-tap (or double-click) the photos to like, like Instagram; it never un-likes.
  const onPhotoTap = () => {
    const now = Date.now();
    if (now - lastTap.current < 300) {
      if (!memory.likedByViewer) like.mutate({ id: memory.id, on: true });
      setBurst(true);
      window.setTimeout(() => setBurst(false), 700);
      lastTap.current = 0;
    } else {
      lastTap.current = now;
    }
  };

  return (
    <article className="border-b border-stone-200 pb-4 dark:border-stone-800 sm:rounded-xl sm:border">
      <header className="flex items-center gap-3 px-3 py-2">
        <Link to={`/u/${memory.author.username}`} className="flex items-center gap-3">
          <Avatar user={memory.author} size="sm" />
          <span className="text-sm font-semibold">{memory.author.username}</span>
        </Link>
        {VISIBILITY_BADGES[memory.visibility] && (
          <span className="ml-auto rounded-full bg-emerald-50 px-2 py-0.5 text-xs text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            {VISIBILITY_BADGES[memory.visibility]}
          </span>
        )}
      </header>

      <div className="relative select-none" onClick={onPhotoTap} data-testid="memory-photos">
        <Carousel media={memory.media} label={`Photos from ${memory.author.username}`} />
        {burst && (
          <span aria-hidden className="pointer-events-none absolute inset-0 flex items-center justify-center text-7xl text-white drop-shadow-lg">
            ♥
          </span>
        )}
      </div>

      <div className="space-y-2 px-3 pt-2">
        <EngagementBar memory={memory} />
        {memory.likeCount > 0 && <p className="text-sm font-semibold">{plural(memory.likeCount, 'like')}</p>}
        <p className="flex flex-wrap items-center gap-2 text-xs text-stone-500 dark:text-stone-400">
          <span>Happened on {formatOccurredOn(memory.occurredOn)}</span>
          <LocationChip memory={memory} />
        </p>
        {caption && (
          <p className="whitespace-pre-line text-sm">
            <Link to={`/u/${memory.author.username}`} className="mr-1 font-semibold">
              {memory.author.username}
            </Link>
            {linkify(truncated ? `${caption.slice(0, CAPTION_PREVIEW).trimEnd()}…` : caption)}
            {truncated && (
              <button type="button" onClick={() => setExpanded(true)} className="ml-1 text-stone-500">
                more
              </button>
            )}
          </p>
        )}
        {memory.commentCount > memory.latestComments.length && (
          <Link to={`/m/${memory.id}#comments`} className="block text-sm text-stone-500 dark:text-stone-400">
            View all {plural(memory.commentCount, 'comment')}
          </Link>
        )}
        {memory.latestComments.map((comment) => (
          <p key={comment.id} className="text-sm">
            <Link to={`/u/${comment.author?.username}`} className="mr-1 font-semibold">
              {comment.author?.username}
            </Link>
            {linkify(comment.body ?? '')}
          </p>
        ))}
      </div>
    </article>
  );
}
