import {
  type InfiniteData,
  keepPreviousData,
  type QueryClient,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { api, withPage } from './client';
import type {
  Album,
  AppNotification,
  Comment,
  GridItem,
  MapMemory,
  Me,
  Memory,
  OnThisDay,
  Page,
  Profile,
  Relationship,
  UploadMetadata,
  UserSummary,
  Visibility,
} from './types';

export const keys = {
  me: ['me'] as const,
  profile: (username: string) => ['profile', username] as const,
  feed: ['feed'] as const,
  memory: (id: string) => ['memory', id] as const,
  comments: (id: string) => ['comments', id] as const,
  grid: (username: string, tab: GridTab) => ['grid', username, tab] as const,
  users: (username: string, list: 'followers' | 'following') => ['users', username, list] as const,
  followRequests: ['follow-requests'] as const,
  closeFriends: ['close-friends'] as const,
  blocks: ['blocks'] as const,
  hashtag: (tag: string) => ['hashtag', tag] as const,
  search: (query: string) => ['search', query] as const,
  onThisDay: (date: string) => ['on-this-day', date] as const,
  map: (bbox: string) => ['map', bbox] as const,
  album: (id: string) => ['album', id] as const,
  albumMemories: (id: string) => ['album-memories', id] as const,
  albums: (username: string) => ['albums', username] as const,
  notifications: ['notifications'] as const,
  unreadNotifications: ['notifications', 'unread'] as const,
};

export type GridTab = 'memories' | 'tagged' | 'saved' | 'albums';

function infinitePage<T>(path: string, limit?: number) {
  return {
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }: { pageParam: string | null }) => api<Page<T>>(withPage(path, pageParam, limit)),
    getNextPageParam: (last: Page<T>) => last.nextCursor ?? undefined,
  };
}

export function useNotifications() {
  return useInfiniteQuery({ queryKey: keys.notifications, ...infinitePage<AppNotification>('/api/notifications') });
}

export function useUnreadNotifications(enabled = true) {
  return useQuery({
    queryKey: keys.unreadNotifications,
    queryFn: () => api<{ count: number }>('/api/notifications/unread-count'),
    enabled,
  });
}

export function useMarkNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('/api/notifications/read', { method: 'POST' }),
    onSuccess: () => queryClient.setQueryData(keys.unreadNotifications, { count: 0 }),
  });
}

export function useMe() {
  return useQuery({ queryKey: keys.me, queryFn: () => api<Me>('/api/users/me') });
}

export function useProfile(username: string) {
  return useQuery({ queryKey: keys.profile(username), queryFn: () => api<Profile>(`/api/users/${username}`) });
}

export function useFeed() {
  return useInfiniteQuery({ queryKey: keys.feed, ...infinitePage<Memory>('/api/feed') });
}

export function useGrid(username: string, tab: GridTab, enabled = true) {
  const path = tab === 'saved' ? '/api/saved' : `/api/users/${username}/${tab === 'memories' ? 'memories' : 'tagged'}`;
  return useInfiniteQuery({ queryKey: keys.grid(username, tab), enabled, ...infinitePage<GridItem>(path, 30) });
}

export function useAlbum(id: string) {
  return useQuery({ queryKey: keys.album(id), queryFn: () => api<Album>(`/api/albums/${id}`) });
}

export function useAlbumMemories(id: string) {
  return useInfiniteQuery({ queryKey: keys.albumMemories(id), ...infinitePage<GridItem>(`/api/albums/${id}/memories`, 30) });
}

export function useProfileAlbums(username: string, enabled = true) {
  return useQuery({
    queryKey: keys.albums(username),
    queryFn: () => api<Page<Album>>(`/api/users/${username}/albums`),
    enabled: enabled && username !== '',
  });
}

/** Every album change can affect the album, its memories, and album lists, so refresh all of them. */
function useAlbumMutation<T, R>(mutationFn: (input: T) => Promise<R>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['album'] });
      client.invalidateQueries({ queryKey: ['album-memories'] });
      client.invalidateQueries({ queryKey: ['albums'] });
    },
  });
}

export function useCreateAlbum() {
  return useAlbumMutation((input: { title: string; description: string; visibility: Visibility }) =>
    api<Album>('/api/albums', { method: 'POST', body: input }));
}

export function useUpdateAlbum(id: string) {
  return useAlbumMutation((input: { title?: string; description?: string; visibility?: Visibility; coverMemoryId?: string }) =>
    api<Album>(`/api/albums/${id}`, { method: 'PATCH', body: input }));
}

export function useDeleteAlbum(id: string) {
  return useAlbumMutation(() => api<void>(`/api/albums/${id}`, { method: 'DELETE' }));
}

export function useAddToAlbum() {
  return useAlbumMutation(({ albumId, memoryIds }: { albumId: string; memoryIds: string[] }) =>
    api<void>(`/api/albums/${albumId}/memories`, { method: 'POST', body: { memoryIds } }));
}

export function useRemoveFromAlbum(id: string) {
  return useAlbumMutation((memoryId: string) => api<void>(`/api/albums/${id}/memories/${memoryId}`, { method: 'DELETE' }));
}

export function useAddCollaborator(id: string) {
  return useAlbumMutation(({ userId, role }: { userId: string; role: 'EDITOR' | 'VIEWER' }) =>
    api<void>(`/api/albums/${id}/collaborators`, { method: 'POST', body: { userId, role } }));
}

/** The owner removes someone ({@code userId}), or a collaborator leaves ({@code 'me'}). */
export function useRemoveCollaborator(id: string) {
  return useAlbumMutation((userId: string) => api<void>(`/api/albums/${id}/collaborators/${userId}`, { method: 'DELETE' }));
}

/** Map markers for a viewport; keeps showing the previous markers while the next box loads. */
export function useMapMemories(bbox: string | null) {
  return useQuery({
    queryKey: keys.map(bbox ?? ''),
    queryFn: () => api<{ items: MapMemory[]; truncated: boolean }>(`/api/memories/map?bbox=${encodeURIComponent(bbox!)}`),
    enabled: bbox !== null,
    placeholderData: keepPreviousData,
  });
}

/** @param date the viewer's local date, so "today" follows their calendar rather than the server's */
export function useOnThisDay(date: string) {
  return useQuery({
    queryKey: keys.onThisDay(date),
    queryFn: () => api<OnThisDay>(`/api/resurface/on-this-day?date=${date}`),
  });
}

export function useSearch(query: string) {
  return useQuery({
    queryKey: keys.search(query),
    queryFn: () => api<{ users: UserSummary[]; hashtags: string[] }>(`/api/search?q=${encodeURIComponent(query)}`),
    enabled: query.length > 0,
    staleTime: 10_000,
  });
}

export function useHashtagGrid(tag: string) {
  return useInfiniteQuery({
    queryKey: keys.hashtag(tag),
    ...infinitePage<GridItem>(`/api/hashtags/${encodeURIComponent(tag)}/memories`, 30),
  });
}

export function useUserList(username: string, list: 'followers' | 'following') {
  return useInfiniteQuery({
    queryKey: keys.users(username, list),
    ...infinitePage<UserSummary>(`/api/users/${username}/${list}`),
  });
}

/** Polls while the memory is processing so the author sees it become ready. */
export function useMemory(id: string) {
  return useQuery({
    queryKey: keys.memory(id),
    queryFn: () => api<Memory>(`/api/memories/${id}`),
    refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 1500 : false),
  });
}

export function useComments(memoryId: string) {
  return useInfiniteQuery({
    queryKey: keys.comments(memoryId),
    ...infinitePage<Comment>(`/api/memories/${memoryId}/comments`),
  });
}

export function useFollowRequests() {
  return useInfiniteQuery({ queryKey: keys.followRequests, ...infinitePage<UserSummary>('/api/follow-requests') });
}

export function useCloseFriends() {
  return useInfiniteQuery({ queryKey: keys.closeFriends, ...infinitePage<UserSummary>('/api/close-friends', 100) });
}

/** Applies an update to a memory wherever it is cached: its detail query and any loaded feed page. */
export function patchMemory(client: QueryClient, id: string, update: (memory: Memory) => Memory) {
  client.setQueryData<Memory>(keys.memory(id), (memory) => (memory ? update(memory) : memory));
  client.setQueryData<InfiniteData<Page<Memory>>>(keys.feed, (feed) =>
    feed
      ? {
          ...feed,
          pages: feed.pages.map((page) => ({
            ...page,
            items: page.items.map((memory) => (memory.id === id ? update(memory) : memory)),
          })),
        }
      : feed,
  );
}

function useOptimisticToggle(
  field: 'likedByViewer' | 'savedByViewer',
  path: (id: string) => string,
  adjust: (memory: Memory, on: boolean) => Partial<Memory>,
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, on }: { id: string; on: boolean }) => api<void>(path(id), { method: on ? 'PUT' : 'DELETE' }),
    onMutate: ({ id, on }) =>
      patchMemory(client, id, (memory) =>
        memory[field] === on ? memory : { ...memory, [field]: on, ...adjust(memory, on) },
      ),
    onError: (_error, { id, on }) =>
      patchMemory(client, id, (memory) =>
        memory[field] !== on ? memory : { ...memory, [field]: !on, ...adjust(memory, !on) },
      ),
    onSettled: (_data, _error, { id }) => {
      if (field === 'savedByViewer') client.invalidateQueries({ queryKey: ['grid'] });
      client.invalidateQueries({ queryKey: keys.memory(id) });
    },
  });
}

export function useLike() {
  return useOptimisticToggle('likedByViewer', (id) => `/api/memories/${id}/like`, (memory, on) => ({
    likeCount: Math.max(0, memory.likeCount + (on ? 1 : -1)),
  }));
}

export function useSave() {
  return useOptimisticToggle('savedByViewer', (id) => `/api/memories/${id}/save`, () => ({}));
}

export function useAddComment(memoryId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: { body: string; parentId?: string }) =>
      api<Comment>(`/api/memories/${memoryId}/comments`, { method: 'POST', body: input }),
    onSuccess: () => invalidateMemory(client, memoryId),
  });
}

export function useDeleteComment(memoryId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) => api<void>(`/api/comments/${commentId}`, { method: 'DELETE' }),
    onSuccess: () => invalidateMemory(client, memoryId),
  });
}

function invalidateMemory(client: QueryClient, memoryId: string) {
  client.invalidateQueries({ queryKey: keys.comments(memoryId) });
  client.invalidateQueries({ queryKey: keys.memory(memoryId) });
  client.invalidateQueries({ queryKey: keys.feed });
}

export function useFollow(username: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (follow: boolean): Promise<Relationship> =>
      follow
        ? (await api<{ relationship: Relationship }>(`/api/users/${username}/follow`, { method: 'POST' })).relationship
        : (await api<void>(`/api/users/${username}/follow`, { method: 'DELETE' }), 'NONE'),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.profile(username) });
      client.invalidateQueries({ queryKey: ['grid', username] });
      client.invalidateQueries({ queryKey: keys.feed });
    },
  });
}

export function useAnswerFollowRequest() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      api<void>(`/api/follow-requests/${userId}/${accept ? 'accept' : 'decline'}`, { method: 'POST' }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.followRequests });
      client.invalidateQueries({ queryKey: ['profile'] });
      client.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

/** Adds or removes a close friend, updating the list immediately and re-syncing with the server afterwards. */
export function useCloseFriendToggle() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ user, add }: { user: UserSummary; add: boolean }) =>
      api<void>(`/api/close-friends/${user.id}`, { method: add ? 'PUT' : 'DELETE' }),
    onMutate: ({ user, add }) =>
      client.setQueryData<InfiniteData<Page<UserSummary>>>(keys.closeFriends, (data) => {
        if (!data) return data;
        const pages = data.pages.map((page) => ({ ...page, items: page.items.filter((item) => item.id !== user.id) }));
        if (add) pages[0] = { ...pages[0], items: [user, ...pages[0].items] };
        return { ...data, pages };
      }),
    onSettled: () => client.invalidateQueries({ queryKey: keys.closeFriends }),
  });
}

export function useBlocks() {
  return useInfiniteQuery({ queryKey: keys.blocks, ...infinitePage<UserSummary>('/api/blocks') });
}

/**
 * Blocks or unblocks a user. Blocking removes follows, requests, close friends, and tags between the two, so
 * almost everything cached may be stale afterwards.
 */
export function useBlock() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, block }: { userId: string; block: boolean }) =>
      api<void>(`/api/blocks/${userId}`, { method: block ? 'PUT' : 'DELETE' }),
    onSuccess: (_data, { block }) => {
      if (block) {
        client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' });
      }
      client.invalidateQueries({ queryKey: keys.blocks });
    },
  });
}

export interface ProfileUpdate {
  displayName?: string;
  bio?: string;
  isPrivate?: boolean;
  avatarUploadId?: string;
}

export function useUpdateMe() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (update: ProfileUpdate) => api<Me>('/api/users/me', { method: 'PATCH', body: update }),
    onSuccess: (me) => {
      client.setQueryData(keys.me, me);
      client.invalidateQueries({ queryKey: keys.profile(me.username) });
    },
  });
}

export interface MemoryInput {
  caption: string | null;
  occurredOn: string;
  location: { name: string | null; latitude: number | null; longitude: number | null } | null;
  visibility: Visibility;
  taggedUsernames: string[];
}

export function useCreateMemory() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: MemoryInput & { uploadIds: string[] }) =>
      api<{ id: string; status: string }>('/api/memories', { method: 'POST', body: input }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.feed });
      client.invalidateQueries({ queryKey: ['grid'] });
      client.invalidateQueries({ queryKey: ['profile'] });
    },
  });
}

export function useUpdateMemory(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<MemoryInput>) => api<Memory>(`/api/memories/${id}`, { method: 'PATCH', body: input }),
    onSuccess: (memory) => {
      client.setQueryData(keys.memory(id), memory);
      client.invalidateQueries({ queryKey: keys.feed });
      client.invalidateQueries({ queryKey: ['grid'] });
    },
  });
}

export function useDeleteMemory() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/memories/${id}`, { method: 'DELETE' }),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: keys.memory(id) });
      client.invalidateQueries({ queryKey: keys.feed });
      client.invalidateQueries({ queryKey: ['grid'] });
      client.invalidateQueries({ queryKey: ['profile'] });
    },
  });
}

export function useRetryMemory(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api<{ id: string; status: string }>(`/api/memories/${id}/retry`, { method: 'POST' }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.memory(id) }),
  });
}

export function fetchUploadMetadata(uploadId: string): Promise<UploadMetadata> {
  return api<UploadMetadata>(`/api/media/uploads/${uploadId}/metadata`);
}
