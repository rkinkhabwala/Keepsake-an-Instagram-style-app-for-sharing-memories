/** Wraps a longitude into [-180, 180), since a Leaflet map panned past the antimeridian reports e.g. 190. */
export function wrapLongitude(lng: number): number {
  return ((((lng + 180) % 360) + 360) % 360) - 180;
}

/**
 * The API's bbox ("minLng,minLat,maxLng,maxLat") for a map viewport. A viewport wider than the world asks for
 * the whole world; one crossing the antimeridian yields minLng > maxLng, which the API understands.
 */
export function viewportToBbox(west: number, south: number, east: number, north: number): string {
  const round = (value: number) => Math.round(value * 10_000) / 10_000;
  const minLat = Math.max(-90, Math.min(90, south));
  const maxLat = Math.max(-90, Math.min(90, north));
  if (![west, south, east, north].every(Number.isFinite)) return '-180,-90,180,90';
  if (east - west >= 360) return `-180,${round(minLat)},180,${round(maxLat)}`;
  const minLng = wrapLongitude(west);
  // East of exactly 180 wraps to -180; keep it at 180 so the box still reaches the antimeridian.
  const maxLng = east === 180 ? 180 : wrapLongitude(east);
  return [minLng, minLat, maxLng, maxLat].map(round).join(',');
}
