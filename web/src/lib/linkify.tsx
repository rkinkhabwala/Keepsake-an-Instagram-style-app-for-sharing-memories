import { Fragment, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

// Same word-boundary rules as the server's parser: "ana@example.com" is not a mention and "a#b" is not a tag.
const TOKEN = /((?<![\p{L}\p{N}_.])@[a-z0-9._]{3,30}|(?<![\p{L}\p{M}\p{N}_&#])#[\p{L}\p{M}\p{N}_]{1,100}(?![\p{L}\p{M}\p{N}_]))/gu;

export function hashtagPath(tag: string): string {
  return `/tags/${encodeURIComponent(tag.replace(/^#/, '').normalize('NFC').toLowerCase())}`;
}

/**
 * Renders user text as plain text, turning @mentions into profile links and #hashtags into tag-page links. Never
 * interprets HTML: every piece is a React text node.
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
        <Link key={index} to={hashtagPath(part)} className="text-emerald-700 dark:text-emerald-400">
          {part}
        </Link>
      );
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}
