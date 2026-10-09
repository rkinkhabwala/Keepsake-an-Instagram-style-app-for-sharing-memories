import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { type GridTab, useBlock, useFollow, useGrid, useProfile } from '../api/queries';
import type { Profile } from '../api/types';
import { AlbumList } from '../components/AlbumList';
import { Avatar } from '../components/Avatar';
import { MemoryGrid } from '../components/MemoryGrid';
import { Button, ErrorNotice, Spinner } from '../components/ui';
import { plural } from '../lib/format';
import { NotFound } from './MemoryDetailPage';

export function ProfilePage() {
  const { username = '' } = useParams();
  const profile = useProfile(username);

  if (profile.isPending) return <Spinner label="Loading profile" />;
  if (profile.isError) {
    return profile.error instanceof ApiError && profile.error.status === 404 ? (
      <NotFound />
    ) : (
      <div className="p-4"><ErrorNotice error={profile.error} /></div>
    );
  }
  return <ProfileView key={username} profile={profile.data} />;
}

function ProfileView({ profile }: { profile: Profile }) {
  const own = profile.relationship === 'SELF';
  const locked = profile.isPrivate && !own && profile.relationship !== 'FOLLOWING';
  const tabs: { id: GridTab; label: string }[] = [
    { id: 'memories', label: 'Memories' },
    { id: 'tagged', label: 'Tagged' },
    ...(own ? [{ id: 'saved' as GridTab, label: 'Saved' }] : []),
    { id: 'albums', label: 'Albums' },
  ];
  const [tab, setTab] = useState<GridTab>('memories');

  return (
    <div className="px-4 py-6">
      <header className="flex gap-6 sm:gap-12">
        <Avatar user={profile} size="lg" />
        <div className="min-w-0 flex-1 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-xl font-semibold">{profile.username}</h1>
            {own ? (
              <Link to="/settings" className="rounded-lg bg-stone-200 px-4 py-2 text-sm font-semibold dark:bg-stone-800">
                Edit profile
              </Link>
            ) : (
              <>
                <FollowButton profile={profile} />
                <BlockButton profile={profile} />
              </>
            )}
          </div>
          <ul className="flex gap-5 text-sm">
            <li><strong>{profile.memoryCount.toLocaleString()}</strong> memories</li>
            <li>
              <Link to={`/u/${profile.username}/followers`}>{plural(profile.followerCount, 'follower')}</Link>
            </li>
            <li>
              <Link to={`/u/${profile.username}/following`}><strong>{profile.followingCount.toLocaleString()}</strong> following</Link>
            </li>
          </ul>
          <div className="text-sm">
            <p className="font-semibold">{profile.displayName}</p>
            {profile.bio && <p className="whitespace-pre-line">{profile.bio}</p>}
          </div>
        </div>
      </header>

      {locked ? (
        <div className="mt-10 border-t border-stone-200 py-12 text-center dark:border-stone-800">
          <p aria-hidden className="text-3xl">🔒</p>
          <h2 className="mt-2 font-semibold">This account is private</h2>
          <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">Follow to see their memories.</p>
        </div>
      ) : (
        <>
          <div role="tablist" aria-label="Profile sections" className="mt-8 flex justify-center gap-8 border-t border-stone-200 dark:border-stone-800">
            {tabs.map((option) => (
              <button
                key={option.id}
                type="button"
                role="tab"
                aria-selected={tab === option.id}
                onClick={() => setTab(option.id)}
                className={`-mt-px border-t py-3 text-xs font-semibold uppercase tracking-wider ${
                  tab === option.id ? 'border-stone-900 dark:border-stone-100' : 'border-transparent text-stone-500'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
          {tab === 'albums' ? (
            <AlbumList username={profile.username} own={own} />
          ) : (
            <ProfileGrid username={profile.username} tab={tab} />
          )}
        </>
      )}
    </div>
  );
}

const EMPTY: Record<GridTab, string> = {
  albums: '',
  memories: 'No memories yet.',
  tagged: 'No memories with this person tagged yet.',
  saved: 'Memories you save show up here, only for you.',
};

function ProfileGrid({ username, tab }: { username: string; tab: GridTab }) {
  const grid = useGrid(username, tab);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = grid;
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  if (grid.isPending) return <Spinner />;
  if (grid.isError) return <ErrorNotice error={grid.error} />;
  return (
    <div role="tabpanel">
      <MemoryGrid items={grid.data.pages.flatMap((page) => page.items)} hasMore={!!hasNextPage} onLoadMore={loadMore} empty={EMPTY[tab]} />
    </div>
  );
}

function BlockButton({ profile }: { profile: Profile }) {
  const block = useBlock();
  const navigate = useNavigate();
  return (
    <>
      <Button
        variant="ghost"
        className="text-red-600"
        disabled={block.isPending}
        onClick={() => {
          const confirmed = window.confirm(
            `Block @${profile.username}? You won't see each other's memories, profiles, or comments, and any follows, ` +
              'close-friend entries, and tags between you are removed. Unblocking later does not restore them.',
          );
          if (confirmed) block.mutate({ userId: profile.id, block: true }, { onSuccess: () => navigate('/') });
        }}
      >
        Block
      </Button>
      <ErrorNotice error={block.error} />
    </>
  );
}

function FollowButton({ profile }: { profile: Profile }) {
  const follow = useFollow(profile.username);
  const following = profile.relationship === 'FOLLOWING';
  const requested = profile.relationship === 'REQUESTED';
  return (
    <>
      <Button
        variant={following || requested ? 'secondary' : 'primary'}
        disabled={follow.isPending}
        onClick={() => {
          if (following && !window.confirm(`Unfollow @${profile.username}?`)) return;
          follow.mutate(!(following || requested));
        }}
      >
        {following ? 'Following' : requested ? 'Requested' : profile.isPrivate ? 'Request to follow' : 'Follow'}
      </Button>
      <ErrorNotice error={follow.error} />
    </>
  );
}
