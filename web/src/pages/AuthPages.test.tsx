import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ME, fakeApi, renderApp } from '../test/utils';
import { LoginPage, SignupPage } from './AuthPages';

describe('auth pages', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows a friendly message for wrong credentials', async () => {
    const api = fakeApi();
    api.on('POST', '/api/auth/refresh', { detail: 'Authentication failed' }, 401);
    api.on('POST', '/api/auth/login', { detail: 'Authentication failed' }, 401);
    renderApp(<LoginPage />, { route: '/login', path: '/login' });

    await userEvent.type(screen.getByLabelText('Username or email'), 'ana');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That username or password is not right.');
  });

  it('signs up and leaves the page', async () => {
    const api = fakeApi();
    api.on('POST', '/api/auth/refresh', { detail: 'Authentication failed' }, 401);
    api.on('POST', '/api/auth/signup', ME, 201);
    renderApp(<SignupPage />, { route: '/signup', path: '/signup' });

    await userEvent.type(screen.getByLabelText(/^Username/), 'Ana');
    await userEvent.type(screen.getByLabelText(/^Display name/), 'Ana');
    await userEvent.type(screen.getByLabelText(/^Email/), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^Password/), 'long-enough-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign up' }));

    expect(await screen.findByText('Navigated away')).toBeInTheDocument();
    expect(api.calls.find((call) => call.path === '/api/auth/signup')?.body).toMatchObject({ username: 'ana' });
  });
});
