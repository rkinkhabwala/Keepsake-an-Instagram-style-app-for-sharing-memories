import { describe, expect, it } from 'vitest';
import { viewportToBbox, wrapLongitude } from './mapBounds';

describe('map bounds', () => {
  it('wraps longitudes into [-180, 180)', () => {
    expect(wrapLongitude(190)).toBe(-170);
    expect(wrapLongitude(-190)).toBe(170);
    expect(wrapLongitude(540)).toBe(-180);
    expect(wrapLongitude(12.5)).toBe(12.5);
  });

  it('turns a viewport into the API bbox', () => {
    expect(viewportToBbox(-10.123456, 35, 30, 60)).toBe('-10.1235,35,30,60');
  });

  it('crosses the antimeridian with minLng > maxLng', () => {
    expect(viewportToBbox(170, -20, 190, -10)).toBe('170,-20,-170,-10');
    expect(viewportToBbox(-190, -20, -170, -10)).toBe('170,-20,-170,-10');
  });

  it('asks for the whole world when zoomed out past it, and clamps latitude', () => {
    expect(viewportToBbox(-300, -95, 300, 95)).toBe('-180,-90,180,90');
    expect(viewportToBbox(-180, -10, 180, 10)).toBe('-180,-10,180,10');
    expect(viewportToBbox(Number.NaN, 0, 1, 1)).toBe('-180,-90,180,90');
  });
});
