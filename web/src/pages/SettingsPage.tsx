import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import {
  useAnswerFollowRequest,
  useCloseFriends,
  useCloseFriendToggle,
  useFollowRequests,
  useMe,
  useUpdateMe,
  useUserList,
} from '../api/queries';
import type { Me } from '../api/types';
import { MAX_IMAGE_BYTES, contentTypeOf, putFile, requestUploadSlots } from '../api/upload';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { ThemePicker } from '../components/ThemePicker';
import { Button, ErrorNotice, Field, Spinner, inputClass } from '../components/ui';

export function SettingsPage() {
  const me = useMe();
  const { logout } = useAuth();
  if (me.isPending) return <Spinner />;
  if (me.isError) return <div className="p-4"><ErrorNotice error={me.error} /></div>;
  return (
    <div className="mx-auto max-w-[600px] space-y-10 px-4 py-6">
      <EditProfile me={me.data} />
      <ThemePicker />
      <FollowRequests />
      <CloseFriends username={me.data.username} />
      <Button variant="secondary" onClick={() => logout()}>Log out</Button>
      <DeleteAccount />
    </div>
  );
}

function EditProfile({ me }: { me: Me }) {
  const update = useUpdateMe();
  const [displayName, setDisplayName] = useState(me.displayName);
  const [bio, setBio] = useState(me.bio ?? '');
  const [avatarState, setAvatarState] = useState<string | null>(null);
  const [isPrivate, setIsPrivate] = useState(me.isPrivate);
  const [saved, setSaved] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSaved(false);
    update.mutate({ displayName, bio }, { onSuccess: () => setSaved(true) });
  };

  const changeAvatar = async (file: File) => {
    const contentType = contentTypeOf(file);
    if (!contentType || file.size > MAX_IMAGE_BYTES) {
      setAvatarState('Choose a JPEG, PNG, HEIC, or WebP photo under 20 MB.');
      return;
    }
    try {
      setAvatarState('Uploading…');
      const [slot] = await requestUploadSlots([{ contentType, sizeBytes: file.size }]);
      await putFile(slot, file, () => undefined);
      await update.mutateAsync({ avatarUploadId: slot.uploadId });
      setAvatarState('Your new photo will appear in a moment.');
    } catch (failure) {
      setAvatarState(failure instanceof Error ? failure.message : 'Upload failed');
    }
  };

  return (
    <section className="space-y-4" aria-labelledby="profile-heading">
      <h1 id="profile-heading" className="text-xl font-semibold">Edit profile</h1>
      <div className="flex items-center gap-4">
        <Avatar user={me} size="lg" />
        <label className="cursor-pointer text-sm font-semibold text-emerald-700 dark:text-emerald-400">
          Change photo
          <input type="file" accept="image/*,.heic,.heif" className="sr-only" onChange={(event) => event.target.files?.[0] && changeAvatar(event.target.files[0])} />
        </label>
      </div>
      {avatarState && <p role="status" className="text-sm text-stone-600 dark:text-stone-400">{avatarState}</p>}
      <form onSubmit={submit} className="space-y-4">
        <Field label="Display name">
          <input required maxLength={100} value={displayName} onChange={(event) => setDisplayName(event.target.value)} className={inputClass} />
        </Field>
        <Field label="Bio" hint={`${bio.length}/150`}>
          <textarea maxLength={150} rows={3} value={bio} onChange={(event) => setBio(event.target.value)} className={inputClass} />
        </Field>
        <ErrorNotice error={update.error} />
        <div className="flex items-center gap-3">
          <Button type="submit" disabled={update.isPending}>Save</Button>
          {saved && <span role="status" className="text-sm text-emerald-700 dark:text-emerald-400">Saved</span>}
        </div>
      </form>
      <label className="flex items-start gap-3 rounded-lg border border-stone-200 p-3 dark:border-stone-800">
        <input
          type="checkbox"
          checked={isPrivate}
          onChange={(event) => {
            const next = event.target.checked;
            setIsPrivate(next);
            update.mutate({ isPrivate: next }, { onError: () => setIsPrivate(!next) });
          }}
          className="mt-1 accent-emerald-700"
        />
        <span>
          <span className="block text-sm font-semibold">Private account</span>
          <span className="block text-xs text-stone-600 dark:text-stone-400">
            Only followers you approve can see your public memories. People who already follow you stay.
          </span>
        </span>
      </label>
    </section>
  );
}

function FollowRequests() {
  const requests = useFollowRequests();
  const answer = useAnswerFollowRequest();
  const people = requests.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className="space-y-3" aria-labelledby="requests-heading">
      <h2 id="requests-heading" className="text-lg font-semibold">Follow requests</h2>
      {requests.isPending ? (
        <Spinner />
      ) : people.length === 0 ? (
        <p className="text-sm text-stone-500 dark:text-stone-400">No pending requests.</p>
      ) : (
        <ul className="space-y-3">
          {people.map((person) => (
            <li key={person.id} className="flex items-center gap-3">
              <Link to={`/u/${person.username}`} className="flex flex-1 items-center gap-3">
                <Avatar user={person} />
                <span className="text-sm font-semibold">{person.username}</span>
              </Link>
              <Button onClick={() => answer.mutate({ userId: person.id, accept: true })} disabled={answer.isPending}>
                Approve
              </Button>
              <Button variant="secondary" onClick={() => answer.mutate({ userId: person.id, accept: false })} disabled={answer.isPending}>
                Decline
              </Button>
            </li>
          ))}
        </ul>
      )}
      <ErrorNotice error={answer.error} />
    </section>
  );
}

/** Deletes the account and everything in it, after the user re-enters their password. */
function DeleteAccount() {
  const { forget } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await api<void>('/api/users/me', { method: 'DELETE', body: { password } });
      forget();
      navigate('/login', { replace: true });
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 403 ? new Error('That password is not right.') : failure);
      setPending(false);
    }
  };

  return (
    <section className="space-y-3 rounded-xl border border-red-200 p-4 dark:border-red-900" aria-labelledby="delete-heading">
      <h2 id="delete-heading" className="text-lg font-semibold text-red-700 dark:text-red-400">Delete account</h2>
      <p className="text-sm text-stone-600 dark:text-stone-400">
        Permanently deletes your memories and photos, likes, comments, saves, follows, close friends, and tags. This
        cannot be undone.
      </p>
      {open ? (
        <form onSubmit={submit} className="space-y-3">
          <Field label="Enter your password to confirm">
            <input type="password" autoComplete="current-password" required value={password}
              onChange={(event) => setPassword(event.target.value)} className={inputClass} />
          </Field>
          <ErrorNotice error={error} />
          <div className="flex gap-2">
            <Button type="submit" variant="danger" disabled={pending || !password}>
              {pending ? 'Deleting…' : 'Delete my account'}
            </Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          </div>
        </form>
      ) : (
        <Button variant="danger" onClick={() => setOpen(true)}>Delete account…</Button>
      )}
    </section>
  );
}

/** Close friends are picked from your followers. */
function CloseFriends({ username }: { username: string }) {
  const followers = useUserList(username, 'followers');
  const closeFriends = useCloseFriends();
  const toggle = useCloseFriendToggle();
  // Local choices show immediately; the server's list takes over again once each change is saved.
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const current = new Set(closeFriends.data?.pages.flatMap((page) => page.items.map((user) => user.id)) ?? []);
  const isCloseFriend = (id: string) => pending[id] ?? current.has(id);
  const people = followers.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className="space-y-3" aria-labelledby="close-friends-heading">
      <h2 id="close-friends-heading" className="text-lg font-semibold">Close friends</h2>
      <p className="text-sm text-stone-600 dark:text-stone-400">Only people on this list see memories you share with close friends.</p>
      {followers.isPending || closeFriends.isPending ? (
        <Spinner />
      ) : people.length === 0 ? (
        <p className="text-sm text-stone-500 dark:text-stone-400">Once people follow you, you can add them here.</p>
      ) : (
        <ul className="space-y-2">
          {people.map((person) => (
            <li key={person.id}>
              <label className="flex items-center gap-3">
                <Avatar user={person} size="sm" />
                <span className="flex-1 text-sm">{person.username}</span>
                <input
                  type="checkbox"
                  aria-label={`Close friend: ${person.username}`}
                  checked={isCloseFriend(person.id)}
                  onChange={(event) => {
                    const add = event.target.checked;
                    setPending((choices) => ({ ...choices, [person.id]: add }));
                    toggle.mutate(
                      { user: person, add },
                      {
                        onSettled: () =>
                          setPending(({ [person.id]: _settled, ...rest }) => rest),
                      },
                    );
                  }}
                  className="h-4 w-4 accent-emerald-700"
                />
              </label>
            </li>
          ))}
        </ul>
      )}
      <ErrorNotice error={toggle.error} />
    </section>
  );
}
