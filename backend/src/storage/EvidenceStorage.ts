import { Readable } from 'node:stream';

export interface StoredEvidenceObject {
  key: string;
  contentType: 'image/jpeg' | 'image/png';
  sizeBytes: number;
}

export interface EvidenceStorage {
  readonly kind: 'local' | 's3';
  put(key: string, contentType: StoredEvidenceObject['contentType'], body: Buffer): Promise<StoredEvidenceObject>;
  get(key: string): Promise<{ contentType: string; sizeBytes?: number; body: Readable }>;
  health(): Promise<boolean>;
}
