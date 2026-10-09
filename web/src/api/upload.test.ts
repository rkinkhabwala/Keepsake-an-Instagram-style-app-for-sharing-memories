import { describe, expect, it } from 'vitest';
import { contentTypeOf, move } from './upload';

describe('contentTypeOf', () => {
  it('uses the browser type, falling back to the extension for HEIC', () => {
    expect(contentTypeOf(new File([''], 'a.jpg', { type: 'image/jpeg' }))).toBe('image/jpeg');
    expect(contentTypeOf(new File([''], 'IMG_0001.HEIC', { type: '' }))).toBe('image/heic');
    expect(contentTypeOf(new File([''], 'notes.txt', { type: 'text/plain' }))).toBeNull();
    expect(contentTypeOf(new File([''], 'anim.gif', { type: 'image/gif' }))).toBeNull();
  });
});

describe('move', () => {
  it('reorders without mutating and ignores out-of-range moves', () => {
    const items = ['a', 'b', 'c'];
    expect(move(items, 0, 2)).toEqual(['b', 'c', 'a']);
    expect(move(items, 2, 0)).toEqual(['c', 'a', 'b']);
    expect(move(items, 0, -1)).toBe(items);
    expect(items).toEqual(['a', 'b', 'c']);
  });
});
