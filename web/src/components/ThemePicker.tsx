import { type ThemeChoice, useTheme } from '../lib/theme';

const OPTIONS: { value: ThemeChoice; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

export function ThemePicker() {
  const [choice, choose] = useTheme();
  return (
    <fieldset className="space-y-2">
      <legend className="text-lg font-semibold">Appearance</legend>
      <div className="inline-flex rounded-lg bg-stone-200 p-1 dark:bg-stone-800">
        {OPTIONS.map((option) => (
          <label
            key={option.value}
            className={`cursor-pointer rounded-md px-4 py-1.5 text-sm font-medium ${
              choice === option.value ? 'bg-white shadow dark:bg-stone-950' : 'text-stone-600 dark:text-stone-400'
            }`}
          >
            <input
              type="radio"
              name="theme"
              value={option.value}
              checked={choice === option.value}
              onChange={() => choose(option.value)}
              className="sr-only"
            />
            {option.label}
          </label>
        ))}
      </div>
      <p className="text-xs text-stone-500 dark:text-stone-400">System follows your device’s light or dark setting.</p>
    </fieldset>
  );
}
