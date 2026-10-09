import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCreateAlbum, useProfileAlbums } from '../api/queries';
import type { Visibility } from '../api/types';
import { BlurhashImage } from './BlurhashImage';
import { VisibilityPicker } from './VisibilityPicker';
import { Button, ErrorNotice, Field, Spinner, inputClass } from './ui';

/** A profile's Albums tab: albums the person owns or collaborates on that you may see. */
export function AlbumList({ username, own }: { username: string; own: boolean }) {
  const albums = useProfileAlbums(username);
  const [creating, setCreating] = useState(false);

  if (albums.isPending) return <Spinner />;
  if (albums.isError) return <ErrorNotice error={albums.error} />;
  const items = albums.data.items;
  return (
    <div className="space-y-4 pt-4">
      {own && (creating ? <NewAlbumForm onCancel={() => setCreating(false)} /> : (
        <Button variant="secondary" onClick={() => setCreating(true)}>New album</Button>
      ))}
      {items.length === 0 ? (
        <p className="py-12 text-center text-sm text-stone-500 dark:text-stone-400">No albums yet.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {items.map((album) => (
            <li key={album.id}>
              <Link to={`/albums/${album.id}`} className="block space-y-1">
                <BlurhashImage src={album.coverThumbUrl} blurhash={album.coverBlurhash} alt="" aspectRatio={1}
                  className="rounded-lg" />
                <span className="block truncate text-sm font-semibold">{album.title}</span>
                <span className="block text-xs text-stone-500">
                  {album.memoryCount === 1 ? '1 memory' : `${album.memoryCount} memories`}
                  {album.owner.username !== username && ` · by @${album.owner.username}`}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NewAlbumForm({ onCancel }: { onCancel: () => void }) {
  const create = useCreateAlbum();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState<Visibility>('FOLLOWERS');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate({ title, description, visibility }, { onSuccess: (album) => navigate(`/albums/${album.id}`) });
  };

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-stone-200 p-4 dark:border-stone-800">
      <Field label="Album title">
        <input required maxLength={100} value={title} onChange={(event) => setTitle(event.target.value)} className={inputClass} />
      </Field>
      <Field label="Description">
        <textarea maxLength={500} rows={2} value={description} onChange={(event) => setDescription(event.target.value)}
          className={inputClass} />
      </Field>
      <VisibilityPicker value={visibility} onChange={setVisibility} />
      <ErrorNotice error={create.error} />
      <div className="flex gap-2">
        <Button type="submit" disabled={create.isPending || !title.trim()}>Create album</Button>
        <Button variant="secondary" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}
