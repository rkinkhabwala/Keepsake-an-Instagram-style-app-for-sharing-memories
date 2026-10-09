import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemePicker } from '../components/ThemePicker';
import { THEME_STORAGE_KEY, isDark, readThemeChoice } from './theme';

/** A prefers-color-scheme media query whose answer the test can flip, notifying listeners. */
function stubSystemTheme(initiallyDark: boolean) {
  const listeners = new Set<() => void>();
  const query = {
    matches: initiallyDark,
    addEventListener: (_: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_: string, listener: () => void) => listeners.delete(listener),
  };
  vi.stubGlobal('matchMedia', () => query);
  return {
    setDark(dark: boolean) {
      query.matches = dark;
      listeners.forEach((listener) => listener());
    },
  };
}

const htmlIsDark = () => document.documentElement.classList.contains('dark');

describe('theme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });
  afterEach(() => vi.unstubAllGlobals());

  it('resolves explicit choices and follows the system for "system"', () => {
    expect(isDark('dark', false)).toBe(true);
    expect(isDark('light', true)).toBe(false);
    expect(isDark('system', true)).toBe(true);
    expect(isDark('system', false)).toBe(false);
  });

  it('defaults to system, ignoring unknown saved values', () => {
    expect(readThemeChoice()).toBe('system');
    localStorage.setItem(THEME_STORAGE_KEY, 'neon');
    expect(readThemeChoice()).toBe('system');
  });

  it('applies and remembers a choice, and follows live system changes only in system mode', async () => {
    const system = stubSystemTheme(false);
    render(<ThemePicker />);
    expect(screen.getByRole('radio', { name: 'System' })).toBeChecked();
    expect(htmlIsDark()).toBe(false);

    act(() => system.setDark(true));
    expect(htmlIsDark()).toBe(true);

    await userEvent.click(screen.getByRole('radio', { name: 'Light' }));
    expect(htmlIsDark()).toBe(false);
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    act(() => system.setDark(true));
    expect(htmlIsDark()).toBe(false);

    await userEvent.click(screen.getByRole('radio', { name: 'Dark' }));
    expect(htmlIsDark()).toBe(true);
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    await userEvent.click(screen.getByRole('radio', { name: 'System' }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
    act(() => system.setDark(false));
    expect(htmlIsDark()).toBe(false);
  });

  it('restores the remembered choice on the next visit', () => {
    stubSystemTheme(false);
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    render(<ThemePicker />);

    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked();
    expect(htmlIsDark()).toBe(true);
  });
});
