import { type DragEvent, type FormEvent, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchUploadMetadata, useCreateMemory } from '../api/queries';
import type { UploadMetadata, Visibility } from '../api/types';
import {
  MAX_FILES,
  MAX_VIDEO_SECONDS,
  contentTypeOf,
  isVideoType,
  maxBytesFor,
  move,
  putFile,
  requestUploadSlots,
  videoDurationSeconds,
} from '../api/upload';
import { PeoplePicker } from '../components/PeoplePicker';
import { VisibilityPicker } from '../components/VisibilityPicker';
import { Button, ErrorNotice, Field, inputClass } from '../components/ui';
import { todayIso } from '../lib/format';

interface Item {
  key: string;
  file: File;
  preview: string;
  uploadId: string | null;
  progress: number;
  error: string | null;
}

let nextKey = 0;

export function CreateMemoryPage() {
  const navigate = useNavigate();
  const create = useCreateMemory();
  const [items, setItems] = useState<Item[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [caption, setCaption] = useState('');
  const [occurredOn, setOccurredOn] = useState(todayIso());
  const [dateTouched, setDateTouched] = useState(false);
  const [locationName, setLocationName] = useState('');
  const [coordinates, setCoordinates] = useState<{ latitude: number; longitude: number } | null>(null);
  const [gpsOffer, setGpsOffer] = useState<UploadMetadata | null>(null);
  const [visibility, setVisibility] = useState<Visibility>('FOLLOWERS');
  const [tagged, setTagged] = useState<string[]>([]);
  const [dragging, setDragging] = useState<number | null>(null);
  const inspected = useRef<string | null>(null);
  const previews = useRef<string[]>([]);

  // Release the local preview URLs when leaving the page.
  useEffect(() => () => previews.current.forEach((url) => URL.revokeObjectURL(url)), []);

  const patch = (key: string, change: Partial<Item>) =>
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));

  // Files upload straight to storage as soon as they are picked, each with its own progress bar.
  const addFiles = async (files: File[]) => {
    setProblem(null);
    const room = MAX_FILES - items.length;
    if (files.length > room) setProblem(`A memory can have at most ${MAX_FILES} photos.`);
    const accepted: { file: File; contentType: string }[] = [];
    for (const file of files.slice(0, Math.max(0, room))) {
      const contentType = contentTypeOf(file);
      if (!contentType) {
        setProblem(`${file.name} is not a JPEG, PNG, HEIC, or WebP photo, or an MP4 or MOV video.`);
      } else if (file.size > maxBytesFor(contentType)) {
        setProblem(`${file.name} is larger than ${isVideoType(contentType) ? '100' : '20'} MB.`);
      } else if (isVideoType(contentType) && (await videoDurationSeconds(file)) > MAX_VIDEO_SECONDS + 0.5) {
        setProblem(`${file.name} is longer than ${MAX_VIDEO_SECONDS} seconds.`);
      } else {
        accepted.push({ file, contentType });
      }
    }
    if (accepted.length === 0) return;
    const added: Item[] = accepted.map(({ file }) => ({
      key: String(nextKey++),
      file,
      preview: URL.createObjectURL(file),
      uploadId: null,
      progress: 0,
      error: null,
    }));
    previews.current.push(...added.map((item) => item.preview));
    setItems((current) => [...current, ...added]);
    try {
      const slots = await requestUploadSlots(accepted.map(({ file, contentType }) => ({ contentType, sizeBytes: file.size })));
      await Promise.all(
        added.map(async (item, index) => {
          try {
            await putFile(slots[index], item.file, (progress) => patch(item.key, { progress }));
            patch(item.key, { uploadId: slots[index].uploadId });
          } catch (failure) {
            patch(item.key, { error: failure instanceof Error ? failure.message : 'Upload failed' });
          }
        }),
      );
    } catch (failure) {
      added.forEach((item) => patch(item.key, { error: failure instanceof Error ? failure.message : 'Upload failed' }));
    }
  };

  // Once the first photo is uploaded, prefill the date from its EXIF and offer (never apply) its GPS position.
  const first = items[0];
  useEffect(() => {
    if (!first?.uploadId || inspected.current === first.uploadId) return;
    inspected.current = first.uploadId;
    fetchUploadMetadata(first.uploadId)
      .then((metadata) => {
        if (metadata.takenOn && !dateTouched) setOccurredOn(metadata.takenOn);
        setGpsOffer(metadata.latitude !== null && metadata.longitude !== null ? metadata : null);
      })
      .catch(() => setGpsOffer(null));
  }, [first?.uploadId, dateTouched]);

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    if (event.dataTransfer.files.length) addFiles([...event.dataTransfer.files]);
  };

  const ready = items.length > 0 && items.every((item) => item.uploadId && !item.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!ready) return;
    const name = locationName.trim() || null;
    create.mutate(
      {
        caption: caption.trim() || null,
        occurredOn,
        location: name || coordinates ? { name, latitude: coordinates?.latitude ?? null, longitude: coordinates?.longitude ?? null } : null,
        visibility,
        taggedUsernames: tagged,
        uploadIds: items.map((item) => item.uploadId!),
      },
      { onSuccess: (result) => navigate(`/m/${result.id}`) },
    );
  };

  return (
    <form onSubmit={submit} className="mx-auto max-w-[600px] space-y-6 px-4 py-6">
      <h1 className="text-xl font-semibold">New memory</h1>

      <div
        onDragOver={(event) => event.preventDefault()}
        onDrop={onDrop}
        className="rounded-xl border-2 border-dashed border-stone-300 p-6 text-center dark:border-stone-700"
      >
        <p className="text-sm text-stone-600 dark:text-stone-400">Drag photos here, or</p>
        <label className="mt-2 inline-block cursor-pointer rounded-lg bg-stone-200 px-4 py-2 text-sm font-semibold dark:bg-stone-800">
          Choose photos
          <input
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif,.heic,.heif,video/mp4,video/quicktime,.mov"
            className="sr-only"
            onChange={(event) => {
              if (event.target.files) addFiles([...event.target.files]);
              event.target.value = '';
            }}
          />
        </label>
        <p className="mt-2 text-xs text-stone-500">
          Up to {MAX_FILES} photos or videos: photos up to 20 MB, videos up to 100 MB and {MAX_VIDEO_SECONDS} seconds.
        </p>
      </div>
      {problem && <p role="alert" className="text-sm text-red-700 dark:text-red-300">{problem}</p>}

      {items.length > 0 && (
        <ol className="grid grid-cols-3 gap-2 sm:grid-cols-5" aria-label="Selected photos">
          {items.map((item, index) => (
            <li
              key={item.key}
              draggable
              onDragStart={() => setDragging(index)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault();
                event.stopPropagation();
                if (dragging !== null) setItems((current) => move(current, dragging, index));
                setDragging(null);
              }}
              className="relative overflow-hidden rounded-lg bg-stone-200 dark:bg-stone-800"
            >
              {isVideoType(item.file.type || contentTypeOf(item.file) || '') ? (
                <video src={item.preview} muted playsInline preload="metadata" aria-label={`Video ${index + 1}`}
                  className="aspect-square w-full object-cover" />
              ) : (
                <img src={item.preview} alt={`Photo ${index + 1}`} className="aspect-square w-full object-cover" />
              )}
              <div className="absolute inset-x-0 bottom-0 h-1 bg-black/20">
                <div
                  role="progressbar"
                  aria-label={`Upload progress for photo ${index + 1}`}
                  aria-valuenow={Math.round(item.progress * 100)}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  className={`h-full ${item.error ? 'bg-red-500' : 'bg-emerald-500'}`}
                  style={{ width: `${item.error ? 100 : item.progress * 100}%` }}
                />
              </div>
              {item.error && <p className="absolute inset-x-0 top-0 bg-red-600/90 p-1 text-xs text-white">{item.error}</p>}
              <div className="absolute right-1 top-1 flex gap-1">
                <button type="button" aria-label={`Move photo ${index + 1} earlier`} disabled={index === 0}
                  onClick={() => setItems((current) => move(current, index, index - 1))}
                  className="rounded bg-white/80 px-1 text-xs text-stone-900 disabled:opacity-30">‹</button>
                <button type="button" aria-label={`Move photo ${index + 1} later`} disabled={index === items.length - 1}
                  onClick={() => setItems((current) => move(current, index, index + 1))}
                  className="rounded bg-white/80 px-1 text-xs text-stone-900 disabled:opacity-30">›</button>
                <button type="button" aria-label={`Remove photo ${index + 1}`}
                  onClick={() => setItems((current) => current.filter((other) => other.key !== item.key))}
                  className="rounded bg-white/80 px-1 text-xs text-stone-900">×</button>
              </div>
            </li>
          ))}
        </ol>
      )}

      <Field label="Caption">
        <textarea value={caption} maxLength={2200} rows={3} onChange={(event) => setCaption(event.target.value)} className={inputClass} />
      </Field>

      <Field label="When did it happen?" hint="Filled in from your first photo when it has a date.">
        <input
          type="date"
          required
          value={occurredOn}
          max={todayIso()}
          onChange={(event) => {
            setDateTouched(true);
            setOccurredOn(event.target.value);
          }}
          className={inputClass}
        />
      </Field>

      <div className="space-y-2">
        <Field label="Where?">
          <input placeholder="Add a place" maxLength={200} value={locationName} onChange={(event) => setLocationName(event.target.value)} className={inputClass} />
        </Field>
        {gpsOffer && !coordinates && (
          <div className="flex items-center justify-between gap-2 rounded-lg bg-stone-100 p-3 text-sm dark:bg-stone-900">
            <span>Your photo has a location ({gpsOffer.latitude?.toFixed(3)}, {gpsOffer.longitude?.toFixed(3)}). Add it?</span>
            <Button variant="secondary" onClick={() => setCoordinates({ latitude: gpsOffer.latitude!, longitude: gpsOffer.longitude! })}>
              Use photo location
            </Button>
          </div>
        )}
        {coordinates && (
          <p className="flex items-center gap-2 text-sm">
            📍 {coordinates.latitude.toFixed(3)}, {coordinates.longitude.toFixed(3)}
            <button type="button" onClick={() => setCoordinates(null)} className="font-semibold text-stone-500">Remove</button>
          </p>
        )}
      </div>

      <PeoplePicker value={tagged} onChange={setTagged} />
      <VisibilityPicker value={visibility} onChange={setVisibility} />

      <ErrorNotice error={create.error} />
      <Button type="submit" disabled={!ready || create.isPending} className="w-full">
        {create.isPending ? 'Sharing…' : items.length > 0 && !ready ? 'Uploading…' : 'Share memory'}
      </Button>
    </form>
  );
}
