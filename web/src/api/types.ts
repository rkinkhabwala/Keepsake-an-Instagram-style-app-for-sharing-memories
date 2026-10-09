export type Relationship = 'SELF' | 'NONE' | 'REQUESTED' | 'FOLLOWING';
export type Visibility = 'PUBLIC' | 'FOLLOWERS' | 'CLOSE_FRIENDS' | 'ONLY_ME';
export type MemoryStatus = 'PROCESSING' | 'READY' | 'FAILED';

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  displayName: string;
  bio: string | null;
  isPrivate: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export interface Me extends AuthUser {
  avatarUrl: string | null;
}

export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface Profile {
  id: string;
  username: string;
  displayName: string;
  bio: string | null;
  avatarUrl: string | null;
  isPrivate: boolean;
  memoryCount: number;
  followerCount: number;
  followingCount: number;
  relationship: Relationship;
}

export interface Location {
  name: string | null;
  latitude: number | null;
  longitude: number | null;
}

export interface MemoryMedia {
  position: number;
  type: 'IMAGE' | 'VIDEO';
  displayUrl: string | null;
  thumbUrl: string | null;
  width: number | null;
  height: number | null;
  blurhash: string | null;
}

export interface Comment {
  id: string;
  parentId: string | null;
  author: UserSummary | null;
  body: string | null;
  deleted: boolean;
  canDelete: boolean;
  createdAt: string;
  replies: Comment[];
}

export interface Memory {
  id: string;
  author: UserSummary;
  caption: string | null;
  occurredOn: string;
  location: Location | null;
  visibility: Visibility;
  status: MemoryStatus;
  likeCount: number;
  commentCount: number;
  likedByViewer: boolean;
  savedByViewer: boolean;
  media: MemoryMedia[];
  taggedPeople: UserSummary[];
  latestComments: Comment[];
  createdAt: string;
}

export interface GridItem {
  id: string;
  thumbUrl: string | null;
  blurhash: string | null;
  mediaCount: number;
  occurredOn: string;
}

export interface OnThisDayYear {
  year: number;
  yearsAgo: number;
  memories: GridItem[];
}

export interface OnThisDay {
  date: string;
  years: OnThisDayYear[];
}

export interface UploadSlot {
  uploadId: string;
  url: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
}

export interface UploadMetadata {
  takenOn: string | null;
  latitude: number | null;
  longitude: number | null;
}
