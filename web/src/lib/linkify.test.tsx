import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { linkify } from './linkify';

describe('linkify', () => {
  it('links @mentions to profiles and keeps everything else as plain text', () => {
    const { container } = render(
      <MemoryRouter>
        <p>{linkify('Lunch with @bo.k and #friends <b>not bold</b>')}</p>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: '@bo.k' })).toHaveAttribute('href', '/u/bo.k');
    expect(screen.getByText('#friends')).toBeInTheDocument();
    expect(container.querySelector('b')).toBeNull();
    expect(container.textContent).toContain('<b>not bold</b>');
  });
});
