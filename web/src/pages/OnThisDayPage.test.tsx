import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { OnThisDayPage, yearsAgoLabel } from './OnThisDayPage';

describe('OnThisDayPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('asks for the local date and shows a section per earlier year', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/resurface\/on-this-day\?date=\d{4}-\d{2}-\d{2}$/, {
      date: '2026-07-04',
      years: [
        { year: 2025, yearsAgo: 1, memories: [{ id: 'm1', thumbUrl: '/t.webp', blurhash: null, mediaCount: 1, occurredOn: '2025-07-04', video: false }] },
        { year: 2023, yearsAgo: 3, memories: [{ id: 'm2', thumbUrl: '/t.webp', blurhash: null, mediaCount: 2, occurredOn: '2023-07-04', video: false }] },
      ],
    });
    renderApp(<OnThisDayPage />, { route: '/on-this-day', path: '/on-this-day' });

    expect(await screen.findByRole('region', { name: '1 year ago' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '3 years ago' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Memory from 2023-07-04' })).toHaveAttribute('href', '/m/m2');
  });

  it('labels years in plain language', () => {
    expect(yearsAgoLabel(1)).toBe('1 year ago');
    expect(yearsAgoLabel(4)).toBe('4 years ago');
  });
});
