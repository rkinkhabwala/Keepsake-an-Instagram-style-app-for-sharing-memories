import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('shows the Keepsake welcome message', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Your moments, remembered.' })).toBeInTheDocument();
  });
});
