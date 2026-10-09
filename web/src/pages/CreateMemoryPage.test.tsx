import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, renderApp, signedIn } from '../test/utils';
import { CreateMemoryPage } from './CreateMemoryPage';

/** Minimal XMLHttpRequest stand-in that "uploads" instantly and records what was sent where. */
class FakeXhr {
  static sent: { method: string; url: string; headers: Record<string, string> }[] = [];
  upload: { onprogress: ((event: { lengthComputable: boolean; loaded: number; total: number }) => void) | null } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  status = 0;
  private method = '';
  private url = '';
  private headers: Record<string, string> = {};
  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }
  send() {
    FakeXhr.sent.push({ method: this.method, url: this.url, headers: this.headers });
    setTimeout(() => {
      this.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 });
      this.status = 200;
      this.onload?.();
    });
  }
}

describe('CreateMemoryPage', () => {
  beforeEach(() => {
    FakeXhr.sent = [];
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:preview', revokeObjectURL: () => undefined }));
  });
  afterEach(() => vi.unstubAllGlobals());

  it('uploads directly to storage, prefills the date from EXIF, offers GPS, and submits in order', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/users\/ana\/(followers|following)/, { items: [], nextCursor: null });
    api.on('POST', '/api/media/upload-urls', ({ body }: { body: unknown }) =>
      (body as { contentType: string }[]).map((item, index) => ({
        uploadId: `up-${index + 1}`,
        url: `http://storage/originals/${index + 1}`,
        method: 'PUT',
        headers: { 'Content-Type': item.contentType },
        expiresAt: '2030-01-01T00:00:00Z',
      })),
    201);
    api.on('GET', '/api/media/uploads/up-1/metadata', { takenOn: '2018-02-14', latitude: 48.8584, longitude: 2.2945 });
    api.on('POST', '/api/memories', { id: 'new-memory', status: 'PROCESSING' }, 202);
    renderApp(<CreateMemoryPage />, { route: '/new', path: '/new' });

    const files = [
      new File(['one'], 'paris.jpg', { type: 'image/jpeg' }),
      new File(['two'], 'IMG_2.HEIC', { type: '' }),
    ];
    await userEvent.upload(screen.getByLabelText('Choose photos'), files);

    await waitFor(() => expect(FakeXhr.sent).toHaveLength(2));
    expect(FakeXhr.sent[1]).toMatchObject({ method: 'PUT', headers: { 'Content-Type': 'image/heic' } });
    expect(await screen.findByDisplayValue('2018-02-14')).toBeInTheDocument();

    // GPS is offered, never applied silently.
    expect(screen.getByText(/Your photo has a location/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Move photo 2 earlier' }));
    await userEvent.type(screen.getByLabelText('Caption'), 'Paris');
    await userEvent.click(screen.getByRole('radio', { name: /Close friends/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Share memory' }));

    await screen.findByText('Navigated away');
    const created = api.calls.find((call) => call.method === 'POST' && call.path === '/api/memories');
    expect(created?.body).toEqual({
      caption: 'Paris',
      occurredOn: '2018-02-14',
      location: null,
      visibility: 'CLOSE_FRIENDS',
      taggedUsernames: [],
      uploadIds: ['up-2', 'up-1'],
    });
  });

  it('rejects unsupported files before uploading', async () => {
    const api = fakeApi();
    signedIn(api);
    api.on('GET', /\/api\/users\/ana\/(followers|following)/, { items: [], nextCursor: null });
    renderApp(<CreateMemoryPage />, { route: '/new', path: '/new' });

    await userEvent.upload(screen.getByLabelText('Choose photos'), [new File(['x'], 'clip.gif', { type: 'image/gif' })], {
      applyAccept: false,
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('clip.gif is not a JPEG, PNG, HEIC, or WebP photo, or an MP4 or MOV video.');
    expect(api.calls.some((call) => call.path === '/api/media/upload-urls')).toBe(false);
  });
});
