import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { fakeApi, memory, signedIn } from './test/utils';

function renderAt(route: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends signed-out visitors to the login page', async () => {
    const api = fakeApi();
    api.on('POST', '/api/auth/refresh', { detail: 'Authentication failed' }, 401);

    renderAt('/');

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
  });

  it('restores the session from the refresh cookie and shows the feed', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/feed', { items: [memory()], nextCursor: null });

    renderAt('/');

    expect(await screen.findByText('Happened on July 4, 2019')).toBeInTheDocument();
  });

  it('shows a not-found page for hidden memories', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', '/api/memories/hidden', { detail: 'Memory not found' }, 404);

    renderAt('/m/hidden');

    expect(await screen.findByRole('heading', { name: 'This isn’t available' })).toBeInTheDocument();
  });
});
