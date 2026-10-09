import { useCallback, useEffect, useState } from 'react';

export type ThemeChoice = 'system' | 'light' | 'dark';

export const THEME_STORAGE_KEY = 'keepsake-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';
const CHOICES: ThemeChoice[] = ['system', 'light', 'dark'];

/** The saved choice; storage can be unavailable (private mode, blocked), in which case it is "system". */
export function readThemeChoice(): ThemeChoice {
  try {
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
    return CHOICES.includes(saved as ThemeChoice) ? (saved as ThemeChoice) : 'system';
  } catch {
    return 'system';
  }
}

function saveThemeChoice(choice: ThemeChoice) {
  try {
    if (choice === 'system') window.localStorage.removeItem(THEME_STORAGE_KEY);
    else window.localStorage.setItem(THEME_STORAGE_KEY, choice);
  } catch {
    // Not remembered, but still applied for this visit.
  }
}

function systemPrefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches;
}

export function isDark(choice: ThemeChoice, prefersDark: boolean): boolean {
  return choice === 'dark' || (choice === 'system' && prefersDark);
}

/** Tailwind's dark: variant keys off the .dark class on <html>. */
export function applyTheme(choice: ThemeChoice) {
  const dark = isDark(choice, systemPrefersDark());
  document.documentElement.classList.toggle('dark', dark);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0c0a09' : '#f7f5f0');
}

/** The current choice and a setter that applies and remembers it; "system" follows OS changes live. */
export function useTheme(): [ThemeChoice, (choice: ThemeChoice) => void] {
  const [choice, setChoice] = useState<ThemeChoice>(readThemeChoice);

  useEffect(() => {
    applyTheme(choice);
    if (choice !== 'system' || typeof window.matchMedia !== 'function') return;
    const query = window.matchMedia(DARK_QUERY);
    const follow = () => applyTheme('system');
    query.addEventListener('change', follow);
    return () => query.removeEventListener('change', follow);
  }, [choice]);

  const choose = useCallback((next: ThemeChoice) => {
    saveThemeChoice(next);
    setChoice(next);
  }, []);

  return [choice, choose];
}
