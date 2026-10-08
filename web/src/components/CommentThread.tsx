import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAddComment, useComments, useDeleteComment } from '../api/queries';
import type { Comment } from '../api/types';
import { linkify } from '../lib/linkify';
import { Avatar } from './Avatar';
import { Button, ErrorNotice, Spinner, inputClass } from './ui';

function CommentRow({
  comment,
  memoryId,
  onReply,
}: {
  comment: Comment;
  memoryId: string;
  onReply?: (comment: Comment) => void;
}) {
  const remove = useDeleteComment(memoryId);
  if (comment.deleted) {
    return <p className="py-1 text-sm italic text-stone-500 dark:text-stone-400">Comment deleted</p>;
  }
  return (
    <div className="flex gap-2 py-1">
      {comment.author && <Avatar user={comment.author} size="sm" />}
      <div className="min-w-0 flex-1">
        <p className="whitespace-pre-line break-words text-sm">
          <Link to={`/u/${comment.author?.username}`} className="mr-1 font-semibold">
            {comment.author?.username}
          </Link>
          {linkify(comment.body ?? '')}
        </p>
        <div className="flex gap-3 text-xs text-stone-500 dark:text-stone-400">
          <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleDateString()}</time>
          {onReply && (
            <button type="button" onClick={() => onReply(comment)} className="font-semibold">
              Reply
            </button>
          )}
          {comment.canDelete && (
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate(comment.id)}
              className="font-semibold text-red-600"
            >
              Delete
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/** Comments with one level of replies, plus the box to add one. */
export function CommentThread({ memoryId }: { memoryId: string }) {
  const comments = useComments(memoryId);
  const add = useAddComment(memoryId);
  const [body, setBody] = useState('');
  const [replyTo, setReplyTo] = useState<Comment | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!body.trim()) return;
    add.mutate(
      { body, parentId: replyTo?.id },
      {
        onSuccess: () => {
          setBody('');
          setReplyTo(null);
        },
      },
    );
  };

  const items = comments.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <section id="comments" aria-label="Comments" className="space-y-3">
      {comments.isPending ? (
        <Spinner label="Loading comments" />
      ) : items.length === 0 ? (
        <p className="text-sm text-stone-500 dark:text-stone-400">No comments yet.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((comment) => (
            <li key={comment.id}>
              <CommentRow comment={comment} memoryId={memoryId} onReply={comment.deleted ? undefined : setReplyTo} />
              {comment.replies.length > 0 && (
                <ul className="ml-10 border-l border-stone-200 pl-3 dark:border-stone-800">
                  {comment.replies.map((reply) => (
                    <li key={reply.id}>
                      <CommentRow comment={reply} memoryId={memoryId} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
      {comments.hasNextPage && (
        <Button variant="ghost" onClick={() => comments.fetchNextPage()} disabled={comments.isFetchingNextPage}>
          Load more comments
        </Button>
      )}
      <form onSubmit={submit} className="space-y-2">
        {replyTo && (
          <p className="flex items-center gap-2 text-xs text-stone-500 dark:text-stone-400">
            Replying to @{replyTo.author?.username}
            <button type="button" onClick={() => setReplyTo(null)} className="font-semibold">
              Cancel
            </button>
          </p>
        )}
        <div className="flex gap-2">
          <input
            aria-label={replyTo ? 'Write a reply' : 'Add a comment'}
            placeholder={replyTo ? 'Write a reply…' : 'Add a comment…'}
            value={body}
            maxLength={500}
            onChange={(event) => setBody(event.target.value)}
            className={inputClass}
          />
          <Button type="submit" disabled={!body.trim() || add.isPending}>
            Post
          </Button>
        </div>
        <ErrorNotice error={add.error} />
      </form>
    </section>
  );
}
