import type { Visibility } from '../api/types';

export const VISIBILITY_OPTIONS: { value: Visibility; label: string; description: string }[] = [
  {
    value: 'PUBLIC',
    label: 'Public',
    description: 'Anyone can see it. If your account is private, only your followers can.',
  },
  { value: 'FOLLOWERS', label: 'Followers', description: 'Only people you have approved as followers.' },
  { value: 'CLOSE_FRIENDS', label: 'Close friends', description: 'Only people on your close friends list.' },
  { value: 'ONLY_ME', label: 'Only me', description: 'Just for you, a private keepsake.' },
];

export function VisibilityPicker({ value, onChange }: { value: Visibility; onChange: (value: Visibility) => void }) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-stone-700 dark:text-stone-300">Who can see this?</legend>
      {VISIBILITY_OPTIONS.map((option) => (
        <label
          key={option.value}
          className={`flex cursor-pointer gap-3 rounded-lg border p-3 ${
            value === option.value
              ? 'border-emerald-600 bg-emerald-50 dark:bg-emerald-950'
              : 'border-stone-200 dark:border-stone-800'
          }`}
        >
          <input
            type="radio"
            name="visibility"
            value={option.value}
            checked={value === option.value}
            onChange={() => onChange(option.value)}
            className="mt-1 accent-emerald-700"
          />
          <span>
            <span className="block text-sm font-semibold">{option.label}</span>
            <span className="block text-xs text-stone-600 dark:text-stone-400">{option.description}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}
