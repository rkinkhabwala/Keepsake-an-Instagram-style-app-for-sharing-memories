import { Fragment, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

const TOKEN = /(@[a-z0-9._]{3,30}|#[\p{L}\p{N}_]+)/gu;

/**
 * Renders user text as plain text, turning @mentions into profile links and styling #hashtags. Never interprets
 * HTML: every piece is a React text node.
 */
export function linkify(text: string): ReactNode[] {
  return text.split(TOKEN).map((part, index) => {
    if (index % 2 === 1 && part.startsWith('@')) {
      return (
        <Link key={index} to={`/u/${part.slice(1)}`} className="font-medium text-emerald-700 dark:text-emerald-400">
          {part}
        </Link>
      );
    }
    if (index % 2 === 1 && part.startsWith('#')) {
      return (
        <span key={index} className="text-emerald-700 dark:text-emerald-400">
          {part}
        </span>
      );
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}
