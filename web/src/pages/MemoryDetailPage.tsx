import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useDeleteMemory, useMemory, useRetryMemory, useUpdateMemory } from '../api/queries';
import type { Memory, Visibility } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { Carousel } from '../components/Carousel';
import { CommentThread } from '../components/CommentThread';
import { EngagementBar, LocationChip, VISIBILITY_BADGES } from '../components/MemoryCard';
import { PeoplePicker } from '../components/PeoplePicker';
import { VisibilityPicker } from '../components/VisibilityPicker';
import { Button, ErrorNotice, Field, Spinner, inputClass } from '../components/ui';
import { formatOccurredOn, plural } from '../lib/format';
import { linkify } from '../lib/linkify';

export function MemoryDetailPage() {
  const { id = '' } = useParams();
  const memory = useMemory(id);

  if (memory.isPending) return <Spinner label="Loading memory" />;
  if (memory.isError) {
    return memory.error instanceof ApiError && memory.error.status === 404 ? (
      <NotFound />
    ) : (
      <div className="p-4"><ErrorNotice error={memory.error} /></div>
    );
  }
  return <MemoryDetail memory={memory.data} />;
}

export function NotFound() {
  return (
    <div className="px-6 py-16 text-center">
      <h1 className="text-lg font-semibold">This isn’t available</h1>
      <p className="mt-2 text-sm text-stone-600 dark:text-stone-400">
        The link may be broken, or the memory may have been removed or shared with fewer people.
      </p>
      <Link to="/" className="mt-4 inline-block font-semibold text-emerald-700 dark:text-emerald-400">Back to your feed</Link>
    </div>
  );
}

function MemoryDetail({ memory }: { memory: Memory }) {
  const { user } = useAuth();
  const isAuthor = user?.id === memory.author.id;
  const [editing, setEditing] = useState(false);

  return (
    <article className="mx-auto max-w-[600px] pb-8 sm:pt-4">
      <header className="flex items-center gap-3 px-4 py-3">
        <Link to={`/u/${memory.author.username}`} className="flex items-center gap-3">
          <Avatar user={memory.author} />
          <span className="font-semibold">{memory.author.username}</span>
        </Link>
        {VISIBILITY_BADGES[memory.visibility] && (
          <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            {VISIBILITY_BADGES[memory.visibility]}
          </span>
        )}
        {isAuthor && memory.status === 'READY' && !editing && (
          <Button variant="ghost" className="ml-auto" onClick={() => setEditing(true)}>Edit</Button>
        )}
      </header>

      {memory.status === 'PROCESSING' && (
        <div role="status" className="mx-4 rounded-xl bg-stone-100 p-8 text-center dark:bg-stone-900">
          <p className="font-semibold">Processing…</p>
          <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">Your photos are being prepared. This page updates by itself.</p>
        </div>
      )}
      {memory.status === 'FAILED' && <FailedNotice memory={memory} />}
      {memory.status === 'READY' && <Carousel media={memory.media} label={`Photos from ${memory.author.username}`} />}

      <div className="space-y-3 px-4 pt-3">
        {editing ? (
          <EditMemoryForm memory={memory} onDone={() => setEditing(false)} />
        ) : (
          <>
            {memory.status === 'READY' && <EngagementBar memory={memory} />}
            {memory.likeCount > 0 && <p className="text-sm font-semibold">{plural(memory.likeCount, 'like')}</p>}
            <p className="flex flex-wrap items-center gap-2 text-sm text-stone-600 dark:text-stone-400">
              <span>Happened on {formatOccurredOn(memory.occurredOn)}</span>
              <LocationChip memory={memory} />
            </p>
            {memory.caption && <p className="whitespace-pre-line text-sm">{linkify(memory.caption)}</p>}
            {memory.taggedPeople.length > 0 && (
              <p className="text-sm">
                <span className="text-stone-600 dark:text-stone-400">With </span>
                {memory.taggedPeople.map((person, index) => (
                  <span key={person.id}>
                    {index > 0 && ', '}
                    <Link to={`/u/${person.username}`} className="font-semibold">@{person.username}</Link>
                  </span>
                ))}
              </p>
            )}
            {isAuthor && <DeleteMemoryButton id={memory.id} username={memory.author.username} />}
          </>
        )}
        {memory.status === 'READY' && !editing && (
          <div className="border-t border-stone-200 pt-3 dark:border-stone-800">
            <CommentThread memoryId={memory.id} />
          </div>
        )}
      </div>
    </article>
  );
}

function FailedNotice({ memory }: { memory: Memory }) {
  const retry = useRetryMemory(memory.id);
  return (
    <div role="alert" className="mx-4 space-y-3 rounded-xl bg-red-50 p-6 text-center dark:bg-red-950">
      <p className="font-semibold text-red-900 dark:text-red-100">We couldn’t process these photos.</p>
      <p className="text-sm text-red-800 dark:text-red-200">Try again, or delete this memory and upload the photos again.</p>
      <Button onClick={() => retry.mutate()} disabled={retry.isPending}>Retry</Button>
      <ErrorNotice error={retry.error} />
    </div>
  );
}

function DeleteMemoryButton({ id, username }: { id: string; username: string }) {
  const remove = useDeleteMemory();
  const navigate = useNavigate();
  return (
    <>
      <Button
        variant="ghost"
        className="text-red-600"
        disabled={remove.isPending}
        onClick={() => {
          if (window.confirm('Delete this memory? This cannot be undone.')) {
            remove.mutate(id, { onSuccess: () => navigate(`/u/${username}`) });
          }
        }}
      >
        Delete memory
      </Button>
      <ErrorNotice error={remove.error} />
    </>
  );
}

function EditMemoryForm({ memory, onDone }: { memory: Memory; onDone: () => void }) {
  const update = useUpdateMemory(memory.id);
  const [caption, setCaption] = useState(memory.caption ?? '');
  const [occurredOn, setOccurredOn] = useState(memory.occurredOn);
  const [locationName, setLocationName] = useState(memory.location?.name ?? '');
  const [keepCoordinates, setKeepCoordinates] = useState(memory.location?.latitude != null);
  const [visibility, setVisibility] = useState<Visibility>(memory.visibility);
  const [tagged, setTagged] = useState(memory.taggedPeople.map((person) => person.username));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const name = locationName.trim() || null;
    const coordinates = keepCoordinates ? memory.location : null;
    update.mutate(
      {
        caption: caption.trim() || null,
        occurredOn,
        location:
          name || coordinates
            ? { name, latitude: coordinates?.latitude ?? null, longitude: coordinates?.longitude ?? null }
            : null,
        visibility,
        taggedUsernames: tagged,
      },
      { onSuccess: onDone },
    );
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Caption">
        <textarea value={caption} maxLength={2200} rows={3} onChange={(event) => setCaption(event.target.value)} className={inputClass} />
      </Field>
      <Field label="When did it happen?">
        <input type="date" required value={occurredOn} onChange={(event) => setOccurredOn(event.target.value)} className={inputClass} />
      </Field>
      <Field label="Where?">
        <input maxLength={200} value={locationName} onChange={(event) => setLocationName(event.target.value)} className={inputClass} />
      </Field>
      {memory.location?.latitude != null && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={keepCoordinates} onChange={(event) => setKeepCoordinates(event.target.checked)} />
          Keep map position ({memory.location.latitude.toFixed(3)}, {memory.location.longitude?.toFixed(3)})
        </label>
      )}
      <PeoplePicker value={tagged} onChange={setTagged} />
      <VisibilityPicker value={visibility} onChange={setVisibility} />
      <ErrorNotice error={update.error} />
      <div className="flex gap-2">
        <Button type="submit" disabled={update.isPending}>Save changes</Button>
        <Button variant="secondary" onClick={onDone}>Cancel</Button>
      </div>
    </form>
  );
}
