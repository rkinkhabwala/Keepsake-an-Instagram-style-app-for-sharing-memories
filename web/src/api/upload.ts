import { api } from './client';
import type { UploadSlot } from './types';

const EXTENSION_TYPES: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  heic: 'image/heic',
  heif: 'image/heif',
};

export const MAX_IMAGE_BYTES = 20 * 1024 * 1024;
export const MAX_FILES = 10;

/** Browsers often report HEIC files with an empty type, so fall back to the extension. */
export function contentTypeOf(file: File): string | null {
  if (file.type && Object.values(EXTENSION_TYPES).includes(file.type)) {
    return file.type;
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  return EXTENSION_TYPES[extension] ?? null;
}

export function requestUploadSlots(files: { contentType: string; sizeBytes: number }[]): Promise<UploadSlot[]> {
  return api<UploadSlot[]>('/api/media/upload-urls', { method: 'POST', body: files });
}

/** PUTs the file straight to storage with the presigned URL, reporting progress from 0 to 1. */
export function putFile(slot: UploadSlot, file: File, onProgress: (fraction: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open(slot.method, slot.url);
    Object.entries(slot.headers).forEach(([name, value]) => request.setRequestHeader(name, value));
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    };
    request.onload = () =>
      request.status >= 200 && request.status < 300
        ? (onProgress(1), resolve())
        : reject(new Error(`Upload failed (${request.status})`));
    request.onerror = () => reject(new Error('Upload failed'));
    request.send(file);
  });
}

/** Moves the item at {@code from} to {@code to}, returning a new array. */
export function move<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length || from === to) return items;
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}
