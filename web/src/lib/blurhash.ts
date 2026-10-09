// Decoder for the BlurHash placeholder format (https://blurha.sh); the server encodes with 4x3 components.
const BASE83 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~';

export function decode83(value: string): number {
  let result = 0;
  for (const char of value) {
    const digit = BASE83.indexOf(char);
    if (digit < 0) throw new Error(`Invalid blurhash character ${char}`);
    result = result * 83 + digit;
  }
  return result;
}

function srgbToLinear(value: number): number {
  const v = value / 255;
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}

function linearToSrgb(value: number): number {
  const v = Math.max(0, Math.min(1, value));
  return v <= 0.0031308 ? Math.trunc(v * 12.92 * 255 + 0.5) : Math.trunc((1.055 * Math.pow(v, 1 / 2.4) - 0.055) * 255 + 0.5);
}

function signPow(value: number, exponent: number): number {
  return Math.sign(value) * Math.pow(Math.abs(value), exponent);
}

/** Decodes a hash to RGBA pixels of the given size; throws on a malformed hash. */
export function decodeBlurhash(hash: string, width: number, height: number): Uint8ClampedArray {
  if (hash.length < 6) throw new Error('Blurhash is too short');
  const sizeFlag = decode83(hash[0]);
  const componentsY = Math.floor(sizeFlag / 9) + 1;
  const componentsX = (sizeFlag % 9) + 1;
  if (hash.length !== 4 + 2 * componentsX * componentsY) throw new Error('Blurhash length mismatch');

  const maximumValue = (decode83(hash[1]) + 1) / 166;
  const colors: number[][] = [];
  const dc = decode83(hash.substring(2, 6));
  colors.push([srgbToLinear(dc >> 16), srgbToLinear((dc >> 8) & 255), srgbToLinear(dc & 255)]);
  for (let i = 1; i < componentsX * componentsY; i++) {
    const ac = decode83(hash.substring(4 + i * 2, 6 + i * 2));
    colors.push([
      signPow((Math.floor(ac / (19 * 19)) - 9) / 9, 2) * maximumValue,
      signPow(((Math.floor(ac / 19) % 19) - 9) / 9, 2) * maximumValue,
      signPow(((ac % 19) - 9) / 9, 2) * maximumValue,
    ]);
  }

  const pixels = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let r = 0;
      let g = 0;
      let b = 0;
      for (let j = 0; j < componentsY; j++) {
        for (let i = 0; i < componentsX; i++) {
          const basis = Math.cos((Math.PI * x * i) / width) * Math.cos((Math.PI * y * j) / height);
          const color = colors[i + j * componentsX];
          r += color[0] * basis;
          g += color[1] * basis;
          b += color[2] * basis;
        }
      }
      const offset = 4 * (x + y * width);
      pixels[offset] = linearToSrgb(r);
      pixels[offset + 1] = linearToSrgb(g);
      pixels[offset + 2] = linearToSrgb(b);
      pixels[offset + 3] = 255;
    }
  }
  return pixels;
}
