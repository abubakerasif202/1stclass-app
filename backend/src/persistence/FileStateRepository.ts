import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { PersistedTransportState, StateRepository } from './StateRepository';

export class FileStateRepository implements StateRepository {
  readonly kind = 'file' as const;
  private readonly path: string;

  constructor(path = process.env.DATA_FILE || './data/transport-state.json') {
    this.path = resolve(path);
  }

  async load(): Promise<PersistedTransportState | null> {
    try {
      return JSON.parse(await readFile(this.path, 'utf8')) as PersistedTransportState;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null;
      throw error;
    }
  }

  async save(state: PersistedTransportState): Promise<void> {
    await mkdir(dirname(this.path), { recursive: true });
    const temporary = `${this.path}.${process.pid}.tmp`;
    await writeFile(temporary, JSON.stringify(state), { encoding: 'utf8', mode: 0o600 });
    await rename(temporary, this.path);
  }

  async health(): Promise<boolean> {
    await mkdir(dirname(this.path), { recursive: true });
    return true;
  }

  async close(): Promise<void> {}
}
