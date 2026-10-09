import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useUserList } from '../api/queries';
import { Avatar } from '../components/Avatar';
import { ErrorNotice, LoadMore, Spinner } from '../components/ui';

export function UserListPage({ list }: { list: 'followers' | 'following' }) {
  const { username = '' } = useParams();
  const users = useUserList(username, list);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = users;
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="mx-auto max-w-[470px] px-4 py-6">
      <h1 className="mb-4 text-lg font-semibold">
        <Link to={`/u/${username}`}>@{username}</Link> · {list === 'followers' ? 'Followers' : 'Following'}
      </h1>
      {users.isPending ? (
        <Spinner />
      ) : users.isError ? (
        users.error instanceof ApiError && users.error.status === 404 ? (
          <p className="text-sm text-stone-600 dark:text-stone-400">This account is private.</p>
        ) : (
          <ErrorNotice error={users.error} />
        )
      ) : (
        <>
          <ul className="space-y-3">
            {users.data.pages.flatMap((page) => page.items).map((user) => (
              <li key={user.id}>
                <Link to={`/u/${user.username}`} className="flex items-center gap-3">
                  <Avatar user={user} />
                  <span>
                    <span className="block text-sm font-semibold">{user.username}</span>
                    <span className="block text-sm text-stone-500">{user.displayName}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <LoadMore onVisible={loadMore} enabled={!!hasNextPage} />
        </>
      )}
    </div>
  );
}
