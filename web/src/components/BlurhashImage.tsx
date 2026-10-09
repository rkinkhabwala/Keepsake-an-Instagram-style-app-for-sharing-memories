import { useEffect, useRef, useState } from 'react';
import { decodeBlurhash } from '../lib/blurhash';

interface Props {
  src: string | null;
  blurhash: string | null;
  alt: string;
  /** width / height; the box keeps this shape before the image loads so the layout doesn't jump. */
  aspectRatio: number;
  className?: string;
}

/** Shows the blurhash placeholder immediately and fades the real image in once it has loaded. */
export function BlurhashImage({ src, blurhash, alt, aspectRatio, className = '' }: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    const context = canvas.current?.getContext('2d');
    if (!context || !blurhash) return;
    try {
      const pixels = decodeBlurhash(blurhash, 32, 32);
      const image = context.createImageData(32, 32);
      image.data.set(pixels);
      context.putImageData(image, 0, 0);
    } catch {
      // A malformed placeholder just leaves the neutral background.
    }
  }, [blurhash]);

  return (
    <div
      className={`relative overflow-hidden bg-stone-200 dark:bg-stone-800 ${className}`}
      style={{ aspectRatio: String(aspectRatio) }}
    >
      {blurhash && (
        <canvas ref={canvas} width={32} height={32} aria-hidden className="absolute inset-0 h-full w-full" />
      )}
      {src && (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          draggable={false}
          onLoad={() => setLoaded(true)}
          className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-300 ${loaded ? 'opacity-100' : 'opacity-0'}`}
        />
      )}
    </div>
  );
}
