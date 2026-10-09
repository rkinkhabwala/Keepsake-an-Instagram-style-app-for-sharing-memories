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

describe('video uploads', () => {
  it('recognises MP4 and MOV, including by extension, with a 100 MB limit', async () => {
    const { contentTypeOf: typeOf, maxBytesFor, isVideoType } = await import('./upload');
    expect(typeOf(new File([''], 'clip.mp4', { type: 'video/mp4' }))).toBe('video/mp4');
    expect(typeOf(new File([''], 'IMG_0001.MOV', { type: '' }))).toBe('video/quicktime');
    expect(typeOf(new File([''], 'clip.webm', { type: 'video/webm' }))).toBeNull();
    expect(isVideoType('video/quicktime')).toBe(true);
    expect(maxBytesFor('video/mp4')).toBe(100 * 1024 * 1024);
    expect(maxBytesFor('image/png')).toBe(20 * 1024 * 1024);
  });
});
