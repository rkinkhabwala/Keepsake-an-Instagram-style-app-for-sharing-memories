import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { SettingsPage } from './SettingsPage';

function settingsApi() {
  const api = fakeApi();
  signedIn(api);
  api.on('GET', /\/api\/follow-requests/, { items: [], nextCursor: null });
  api.on('GET', /\/api\/close-friends/, { items: [], nextCursor: null });
  api.on('GET', /\/api\/users\/ana\/followers/, { items: [], nextCursor: null });
  return api;
}

describe('SettingsPage account deletion', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('asks for the password and explains a wrong one', async () => {
    const api = settingsApi();
    api.on('DELETE', '/api/users/me', { detail: 'Password is incorrect' }, 403);
    renderApp(<SettingsPage />, { route: '/settings', path: '/settings' });

    await userEvent.click(await screen.findByRole('button', { name: 'Delete account…' }));
    await userEvent.type(screen.getByLabelText('Enter your password to confirm'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That password is not right.');
    expect(api.calls.find((call) => call.method === 'DELETE')?.body).toEqual({ password: 'wrong' });
  });

  it('deletes the account and leaves the signed-in area', async () => {
    const api = settingsApi();
    api.on('DELETE', '/api/users/me', null, 204);
    renderApp(<SettingsPage />, { route: '/settings', path: '/settings' });

    await userEvent.click(await screen.findByRole('button', { name: 'Delete account…' }));
    await userEvent.type(screen.getByLabelText('Enter your password to confirm'), 'keepsake-e2e-password');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByText('Navigated away')).toBeInTheDocument();
  });
});
