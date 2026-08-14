import { createReadStream } from 'node:fs';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve, sep } from 'node:path';
import { EvidenceStorage, StoredEvidenceObject } from './EvidenceStorage';

export class LocalDevelopmentEvidenceStorage implements EvidenceStorage {
  readonly kind = 'local' as const;
  private readonly root: string;

  constructor(root = process.env.STORAGE_DIR || './data/evidence') { this.root = resolve(root); }

  private pathFor(key: string): string {
    const path = resolve(this.root, key);
    if (!path.startsWith(`${this.root}${sep}`)) throw new Error('Invalid storage key');
    return path;
  }

  async put(key: string, contentType: StoredEvidenceObject['contentType'], body: Buffer) {
    const path = this.pathFor(key);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, body, { mode: 0o600 });
    return { key, contentType, sizeBytes: body.length };
  }

  async get(key: string) {
    const path = this.pathFor(key);
    const info = await stat(path);
    const header = (await readFile(path)).subarray(0, 8);
    const contentType = header[0] === 0x89 ? 'image/png' : 'image/jpeg';
    return { contentType, sizeBytes: info.size, body: createReadStream(path) };
  }

  async health() { await mkdir(this.root, { recursive: true }); return true; }
}
