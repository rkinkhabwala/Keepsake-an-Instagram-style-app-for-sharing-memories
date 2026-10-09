import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeApi } from '../test/utils';
import { ApiError, api, onSessionExpired, refreshSession, setAccessToken } from './client';

describe('api client', () => {
  beforeEach(() => setAccessToken(null));
  afterEach(() => vi.unstubAllGlobals());

  it('sends the access token and parses JSON', async () => {
    const server = fakeApi();
    server.on('GET', '/api/users/me', { username: 'ana' });
    setAccessToken('token-1');

    await expect(api('/api/users/me')).resolves.toEqual({ username: 'ana' });
    const fetchMock = vi.mocked(fetch);
    expect((fetchMock.mock.calls[0][1]?.headers as Record<string, string>).Authorization).toBe('Bearer token-1');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    const server = fakeApi();
    setAccessToken('expired');
    server.on('GET', '/api/feed', () => {
      const authorization = (vi.mocked(fetch).mock.lastCall?.[1]?.headers as Record<string, string>).Authorization;
      if (authorization !== 'Bearer fresh') throw new Error('retried without the new token');
      return { items: [], nextCursor: null };
    });
    server.on('POST', '/api/auth/refresh', { accessToken: 'fresh', user: {} });
    let firstAttempt = true;
    const realFetch = vi.mocked(fetch).getMockImplementation()!;
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      if (input === '/api/feed' && firstAttempt) {
        firstAttempt = false;
        return new Response(JSON.stringify({ detail: 'expired' }), { status: 401 });
      }
      return realFetch(input, init);
    });

    await expect(api('/api/feed')).resolves.toEqual({ items: [], nextCursor: null });
    expect(server.calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'POST /api/auth/refresh',
      'GET /api/feed',
    ]);
  });

  it('shares one refresh between concurrent callers, since refresh tokens rotate', async () => {
    const server = fakeApi();
    server.on('POST', '/api/auth/refresh', { accessToken: 'fresh', user: {} });

    const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

    expect(first).toBe(second);
    expect(server.calls.filter((call) => call.path === '/api/auth/refresh')).toHaveLength(1);
  });

  it('reports an expired session when refresh fails', async () => {
    const server = fakeApi();
    server.on('GET', '/api/feed', { detail: 'Authentication is required' }, 401);
    server.on('POST', '/api/auth/refresh', { detail: 'Authentication failed' }, 401);
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    await expect(api('/api/feed')).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('turns problem details into ApiError with field errors', async () => {
    const server = fakeApi();
    server.on('POST', '/api/auth/signup', { detail: 'Request validation failed', errors: { username: 'bad' } }, 400);

    const error = await api('/api/auth/signup', { method: 'POST', body: {} }).catch((caught) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, message: 'Request validation failed', fieldErrors: { username: 'bad' } });
  });
});
