import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { hashtagPath, linkify } from './linkify';

function renderText(text: string) {
  return render(
    <MemoryRouter>
      <p>{linkify(text)}</p>
    </MemoryRouter>,
  );
}

describe('linkify', () => {
  it('links @mentions and #hashtags and keeps everything else as plain text', () => {
    const { container } = renderText('Lunch with @bo.k and #Friends <b>not bold</b>');

    expect(screen.getByRole('link', { name: '@bo.k' })).toHaveAttribute('href', '/u/bo.k');
    expect(screen.getByRole('link', { name: '#Friends' })).toHaveAttribute('href', '/tags/friends');
    expect(container.querySelector('b')).toBeNull();
    expect(container.textContent).toContain('<b>not bold</b>');
  });

  it('does not link emails or tags inside words, like the server', () => {
    renderText('mail ana@keepsake.test or ana#keepsake, &#38; #日本');

    expect(screen.getAllByRole('link').map((link) => link.textContent)).toEqual(['#日本']);
  });

  it('builds tag paths that match the server normalization', () => {
    expect(hashtagPath('#Café')).toBe(`/tags/${encodeURIComponent('café')}`);
  });
});
