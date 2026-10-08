import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import type { AuthResponse, Memory } from '../api/types';
import { AuthProvider } from '../auth/AuthContext';

type Handler = (request: { method: string; path: string; body: unknown }) => unknown;

export interface FakeApi {
  calls: { method: string; path: string; body: unknown }[];
  on: (method: string, path: string | RegExp, handler: Handler | unknown, status?: number) => void;
}

/** Replaces fetch with a router over method + path; unmatched requests fail the test loudly. */
export function fakeApi(): FakeApi {
  const routes: { method: string; path: string | RegExp; handler: Handler | unknown; status: number }[] = [];
  const calls: FakeApi['calls'] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init: RequestInit = {}) => {
      const method = (init.method ?? 'GET').toUpperCase();
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
      calls.push({ method, path: input, body });
      const route = [...routes]
        .reverse()
        .find((candidate) => candidate.method === method && (typeof candidate.path === 'string' ? candidate.path === input : candidate.path.test(input)));
      if (!route) throw new Error(`Unexpected request ${method} ${input}`);
      const payload = typeof route.handler === 'function' ? (route.handler as Handler)({ method, path: input, body }) : route.handler;
      return new Response(route.status === 204 ? null : JSON.stringify(payload ?? {}), {
        status: route.status,
        headers: { 'Content-Type': route.status >= 400 ? 'application/problem+json' : 'application/json' },
      });
    }),
  );
  return {
    calls,
    on: (method, path, handler, status = 200) => routes.push({ method, path, handler, status }),
  };
}

export const ME: AuthResponse = {
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  user: { id: 'u-me', username: 'ana', email: 'ana@example.test', displayName: 'Ana', bio: null, isPrivate: false },
};

/** A signed-in session: the app restores it from the refresh cookie on load. */
export function signedIn(api: FakeApi) {
  api.on('POST', '/api/auth/refresh', ME);
  api.on('GET', '/api/users/me', { ...ME.user, avatarUrl: null });
}

export function renderApp(ui: ReactElement, { route = '/', path = '*' }: { route?: string; path?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<p>Navigated away</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

export function memory(overrides: Partial<Memory> = {}): Memory {
  return {
    id: 'm-1',
    author: { id: 'u-bo', username: 'bo', displayName: 'Bo', avatarUrl: null },
    caption: 'Sunset at the pier',
    occurredOn: '2019-07-04',
    location: { name: 'Santa Monica', latitude: null, longitude: null },
    visibility: 'PUBLIC',
    status: 'READY',
    likeCount: 2,
    commentCount: 0,
    likedByViewer: false,
    savedByViewer: false,
    media: [{ position: 0, type: 'IMAGE', displayUrl: '/d.webp', thumbUrl: '/t.webp', width: 1080, height: 720, blurhash: 'L00000fQfQfQfQfQfQfQfQfQfQfQ' }],
    taggedPeople: [],
    latestComments: [],
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}
