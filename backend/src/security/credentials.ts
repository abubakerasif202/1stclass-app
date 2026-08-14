import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

const KEY_LENGTH = 64;
const COST = 32768;

export function hashCredential(value: string): string {
  const salt = randomBytes(16);
  const hash = scryptSync(value, salt, KEY_LENGTH, {
    N: COST, r: 8, p: 1, maxmem: 64 * 1024 * 1024
  });
  return `scrypt$${COST}$8$1$${salt.toString('base64')}$${hash.toString('base64')}`;
}

export function verifyCredential(value: string, encoded: string): boolean {
  const [algorithm, cost, r, p, saltValue, hashValue] = encoded.split('$');
  if (algorithm !== 'scrypt' || !saltValue || !hashValue) return false;
  try {
    const expected = Buffer.from(hashValue, 'base64');
    const actual = scryptSync(value, Buffer.from(saltValue, 'base64'), expected.length, {
      N: Number(cost), r: Number(r), p: Number(p), maxmem: 64 * 1024 * 1024
    });
    return expected.length === actual.length && timingSafeEqual(expected, actual);
  } catch {
    return false;
  }
}
