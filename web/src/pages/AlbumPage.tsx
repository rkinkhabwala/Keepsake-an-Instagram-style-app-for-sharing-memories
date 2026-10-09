import { type FormEvent, useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  useAddCollaborator,
  useAddToAlbum,
  useAlbum,
  useAlbumMemories,
  useDeleteAlbum,
  useGrid,
  useMe,
  useRemoveCollaborator,
  useRemoveFromAlbum,
  useUpdateAlbum,
  useUserList,
} from '../api/queries';
import type { Album, Visibility } from '../api/types';
import { Avatar } from '../components/Avatar';
import { BlurhashImage } from '../components/BlurhashImage';
import { MemoryGrid } from '../components/MemoryGrid';
import { VISIBILITY_BADGES } from '../components/MemoryCard';
import { VisibilityPicker } from '../components/VisibilityPicker';
import { Button, ErrorNotice, Field, Spinner, inputClass } from '../components/ui';
import { NotFound } from './MemoryDetailPage';

export function AlbumPage() {
  const { id = '' } = useParams();
  const album = useAlbum(id);
  if (album.isPending) return <Spinner label="Loading album" />;
  if (album.isError) {
    return album.error instanceof ApiError && album.error.status === 404 ? (
      <NotFound />
    ) : (
      <div className="p-4"><ErrorNotice error={album.error} /></div>
    );
  }
  return <AlbumView album={album.data} />;
}

function AlbumView({ album }: { album: Album }) {
  const owner = album.viewerRole === 'OWNER';
  const canAdd = owner || album.viewerRole === 'EDITOR';
  const [panel, setPanel] = useState<'none' | 'edit' | 'add' | 'people'>('none');
  const memories = useAlbumMemories(album.id);
  const remove = useRemoveFromAlbum(album.id);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = memories;
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const items = memories.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="space-y-6 px-4 py-6">
      <header className="flex gap-4">
        <div className="w-24 shrink-0 sm:w-36">
          <BlurhashImage src={album.coverThumbUrl} blurhash={album.coverBlurhash} alt="" aspectRatio={1} className="rounded-xl" />
        </div>
        <div className="min-w-0 space-y-2">
          <h1 className="text-xl font-semibold">{album.title}</h1>
          {album.description && <p className="whitespace-pre-line text-sm">{album.description}</p>}
          <p className="text-sm text-stone-600 dark:text-stone-400">
            By <Link to={`/u/${album.owner.username}`} className="font-semibold">@{album.owner.username}</Link>
            {' · '}{album.memoryCount === 1 ? '1 memory' : `${album.memoryCount} memories`}
            {VISIBILITY_BADGES[album.visibility] && ` · ${VISIBILITY_BADGES[album.visibility]}`}
          </p>
          {album.collaborators.length > 0 && (
            <ul aria-label="Collaborators" className="flex flex-wrap gap-2">
              {album.collaborators.map(({ user, role }) => (
                <li key={user.id} className="flex items-center gap-1 rounded-full bg-stone-100 py-0.5 pl-0.5 pr-2 text-xs dark:bg-stone-800">
                  <Avatar user={user} size="sm" />@{user.username}
                  <span className="text-stone-500">{role === 'EDITOR' ? 'can add' : 'can view'}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap gap-2">
            {canAdd && <Button variant="secondary" onClick={() => setPanel(panel === 'add' ? 'none' : 'add')}>Add memories</Button>}
            {owner && <Button variant="secondary" onClick={() => setPanel(panel === 'people' ? 'none' : 'people')}>Collaborators</Button>}
            {owner && <Button variant="ghost" onClick={() => setPanel(panel === 'edit' ? 'none' : 'edit')}>Edit</Button>}
            {album.viewerRole === 'EDITOR' || album.viewerRole === 'VIEWER' ? <LeaveButton album={album} /> : null}
          </div>
        </div>
      </header>

      {panel === 'edit' && <EditAlbum album={album} onDone={() => setPanel('none')} />}
      {panel === 'add' && <AddMemories album={album} onDone={() => setPanel('none')} />}
      {panel === 'people' && <ManageCollaborators album={album} />}

      {memories.isPending ? (
        <Spinner />
      ) : (
        <>
          <MemoryGrid items={items} hasMore={!!hasNextPage} onLoadMore={loadMore}
            empty={canAdd ? 'Add memories to start this album.' : 'No memories here you can see yet.'} />
          {owner && items.length > 0 && (
            <details className="text-sm">
              <summary className="cursor-pointer text-stone-600 dark:text-stone-400">Remove memories</summary>
              <ul className="mt-2 space-y-1">
                {items.map((item) => (
                  <li key={item.id} className="flex items-center gap-2">
                    <Link to={`/m/${item.id}`} className="flex-1">Memory from {item.occurredOn}</Link>
                    <Button variant="ghost" className="text-red-600" disabled={remove.isPending}
                      onClick={() => remove.mutate(item.id)}>Remove</Button>
                  </li>
                ))}
              </ul>
              <ErrorNotice error={remove.error} />
            </details>
          )}
        </>
      )}
    </div>
  );
}

function LeaveButton({ album }: { album: Album }) {
  const leave = useRemoveCollaborator(album.id);
  const navigate = useNavigate();
  return (
    <>
      <Button variant="ghost" className="text-red-600" disabled={leave.isPending}
        onClick={() => {
          if (window.confirm('Leave this album? Memories you added will be removed from it.')) {
            leave.mutate('me', { onSuccess: () => navigate('/') });
          }
        }}>
        Leave album
      </Button>
      <ErrorNotice error={leave.error} />
    </>
  );
}

function EditAlbum({ album, onDone }: { album: Album; onDone: () => void }) {
  const update = useUpdateAlbum(album.id);
  const remove = useDeleteAlbum(album.id);
  const navigate = useNavigate();
  const [title, setTitle] = useState(album.title);
  const [description, setDescription] = useState(album.description ?? '');
  const [visibility, setVisibility] = useState<Visibility>(album.visibility);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    update.mutate({ title, description, visibility }, { onSuccess: onDone });
  };

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-stone-200 p-4 dark:border-stone-800">
      <Field label="Album title">
        <input required maxLength={100} value={title} onChange={(event) => setTitle(event.target.value)} className={inputClass} />
      </Field>
      <Field label="Description">
        <textarea maxLength={500} rows={2} value={description} onChange={(event) => setDescription(event.target.value)} className={inputClass} />
      </Field>
      <VisibilityPicker value={visibility} onChange={setVisibility} />
      <ErrorNotice error={update.error ?? remove.error} />
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={update.isPending}>Save</Button>
        <Button variant="secondary" onClick={onDone}>Cancel</Button>
        <Button variant="danger" className="ml-auto" disabled={remove.isPending}
          onClick={() => {
            if (window.confirm('Delete this album? The memories themselves are kept.')) {
              remove.mutate(undefined, { onSuccess: () => navigate(`/u/${album.owner.username}`) });
            }
          }}>
          Delete album
        </Button>
      </div>
    </form>
  );
}

/** Pick from your own memories; only memories you wrote can be added. */
function AddMemories({ album, onDone }: { album: Album; onDone: () => void }) {
  const me = useMe();
  const mine = useGrid(me.data?.username ?? '', 'memories', !!me.data);
  const add = useAddToAlbum();
  const [selected, setSelected] = useState<string[]>([]);
  const items = (mine.data?.pages.flatMap((page) => page.items) ?? []);
  const ownItems = me.data ? items : [];

  return (
    <section aria-label="Add memories" className="space-y-3 rounded-xl border border-stone-200 p-4 dark:border-stone-800">
      <p className="text-sm text-stone-600 dark:text-stone-400">Choose your memories to add.</p>
      {mine.isPending ? <Spinner /> : (
        <ul className="grid grid-cols-4 gap-1 sm:grid-cols-6">
          {ownItems.map((item) => {
            const checked = selected.includes(item.id);
            return (
              <li key={item.id}>
                <label className={`relative block cursor-pointer rounded ${checked ? 'ring-2 ring-emerald-600' : ''}`}>
                  <input type="checkbox" className="sr-only" checked={checked}
                    aria-label={`Memory from ${item.occurredOn}`}
                    onChange={() => setSelected(checked ? selected.filter((id) => id !== item.id) : [...selected, item.id])} />
                  <BlurhashImage src={item.thumbUrl} blurhash={item.blurhash} alt="" aspectRatio={1} />
                </label>
              </li>
            );
          })}
        </ul>
      )}
      <ErrorNotice error={add.error} />
      <div className="flex gap-2">
        <Button disabled={selected.length === 0 || add.isPending}
          onClick={() => add.mutate({ albumId: album.id, memoryIds: selected }, { onSuccess: onDone })}>
          Add {selected.length > 0 ? selected.length : ''}
        </Button>
        <Button variant="secondary" onClick={onDone}>Cancel</Button>
      </div>
    </section>
  );
}

/** The owner adds followers directly (no invite step) and removes collaborators. */
function ManageCollaborators({ album }: { album: Album }) {
  const followers = useUserList(album.owner.username, 'followers');
  const addCollaborator = useAddCollaborator(album.id);
  const removeCollaborator = useRemoveCollaborator(album.id);
  const current = new Set(album.collaborators.map(({ user }) => user.id));
  const candidates = (followers.data?.pages.flatMap((page) => page.items) ?? []).filter((user) => !current.has(user.id));

  return (
    <section aria-label="Manage collaborators" className="space-y-3 rounded-xl border border-stone-200 p-4 dark:border-stone-800">
      {album.collaborators.length > 0 && (
        <ul className="space-y-2">
          {album.collaborators.map(({ user, role }) => (
            <li key={user.id} className="flex items-center gap-2 text-sm">
              <Avatar user={user} size="sm" /><span className="flex-1">@{user.username} · {role === 'EDITOR' ? 'can add' : 'can view'}</span>
              <Button variant="ghost" className="text-red-600" disabled={removeCollaborator.isPending}
                onClick={() => removeCollaborator.mutate(user.id)}>Remove</Button>
            </li>
          ))}
        </ul>
      )}
      <p className="text-sm text-stone-600 dark:text-stone-400">Add people who follow you:</p>
      {followers.isPending ? <Spinner /> : candidates.length === 0 ? (
        <p className="text-sm text-stone-500">No more followers to add.</p>
      ) : (
        <ul className="space-y-2">
          {candidates.map((user) => (
            <li key={user.id} className="flex items-center gap-2 text-sm">
              <Avatar user={user} size="sm" /><span className="flex-1">@{user.username}</span>
              <Button variant="secondary" disabled={addCollaborator.isPending}
                onClick={() => addCollaborator.mutate({ userId: user.id, role: 'EDITOR' })}>Add as editor</Button>
              <Button variant="ghost" disabled={addCollaborator.isPending}
                onClick={() => addCollaborator.mutate({ userId: user.id, role: 'VIEWER' })}>Viewer</Button>
            </li>
          ))}
        </ul>
      )}
      <ErrorNotice error={addCollaborator.error ?? removeCollaborator.error} />
    </section>
  );
}
