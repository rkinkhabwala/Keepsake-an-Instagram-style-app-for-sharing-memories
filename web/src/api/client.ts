import type { AuthResponse } from './types';

/** An RFC 7807 problem response from the API. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, detail: string, fieldErrors: Record<string, string> = {}) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

// The access token lives only in memory; the refresh token is an httpOnly cookie the browser sends to /api/auth.
let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
const expiryListeners = new Set<() => void>();

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function onSessionExpired(listener: () => void): () => void {
  expiryListeners.add(listener);
  return () => expiryListeners.delete(listener);
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request: refresh tokens rotate
 * and reusing an old one revokes the whole session, so two parallel refreshes would log the user out.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' });
        if (!response.ok) {
          setAccessToken(null);
          return null;
        }
        const session = (await response.json()) as AuthResponse;
        setAccessToken(session.accessToken);
        return session;
      } catch {
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);
  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    const session = await refreshSession();
    if (session) {
      response = await send(path, options);
    }
    if (response.status === 401) {
      setAccessToken(null);
      expiryListeners.forEach((listener) => listener());
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function send(path: string, { method = 'GET', body }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  return fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { detail?: string; errors?: Record<string, string> };
    return new ApiError(response.status, problem.detail ?? response.statusText, problem.errors ?? {});
  } catch {
    return new ApiError(response.status, response.statusText || 'Request failed');
  }
}

export function withPage(path: string, cursor: string | null | undefined, limit?: number): string {
  const params = new URLSearchParams();
  if (cursor) params.set('cursor', cursor);
  if (limit) params.set('limit', String(limit));
  const query = params.toString();
  if (!query) return path;
  return `${path}${path.includes('?') ? '&' : '?'}${query}`;
}
