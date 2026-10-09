import { describe, expect, it } from 'vitest';
import { decode83, decodeBlurhash } from './blurhash';

const BASE83 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~';

function encode83(value: number, length: number): string {
  let result = '';
  for (let position = 1; position <= length; position++) {
    result += BASE83[Math.floor(value / 83 ** (length - position)) % 83];
  }
  return result;
}

describe('decodeBlurhash', () => {
  it('decodes a single-component hash to its solid colour', () => {
    const hash = `00${encode83(0xff8000, 4)}`;

    const pixels = decodeBlurhash(hash, 4, 4);

    expect(pixels).toHaveLength(4 * 4 * 4);
    for (let offset = 0; offset < pixels.length; offset += 4) {
      expect([...pixels.slice(offset, offset + 4)]).toEqual([255, 128, 0, 255]);
    }
  });

  it('decodes a 4x3 hash like those the server produces', () => {
    const hash = 'LKTI:j|cfQ|c|co1fQo1fQfQfQfQ';

    const pixels = decodeBlurhash(hash, 8, 8);

    expect(decode83(hash[0])).toBe(3 + 2 * 9);
    expect(pixels.every((value, index) => index % 4 !== 3 || value === 255)).toBe(true);
  });

  it('rejects malformed hashes', () => {
    expect(() => decodeBlurhash('L0', 4, 4)).toThrow();
    expect(() => decodeBlurhash('L00000fQ', 4, 4)).toThrow(/length/);
    expect(() => decodeBlurhash('00 ' + '000', 4, 4)).toThrow(/character/);
  });
});
