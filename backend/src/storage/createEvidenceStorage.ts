import { EvidenceStorage } from './EvidenceStorage';
import { LocalDevelopmentEvidenceStorage } from './LocalDevelopmentEvidenceStorage';
import { S3EvidenceStorage } from './S3EvidenceStorage';

export function createEvidenceStorage(): EvidenceStorage {
  const bucket = process.env.OBJECT_STORAGE_BUCKET;
  if (bucket) return new S3EvidenceStorage(bucket, process.env.OBJECT_STORAGE_PREFIX || 'evidence/');
  if (process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging') {
    throw new Error('OBJECT_STORAGE_BUCKET is required outside development and tests');
  }
  return new LocalDevelopmentEvidenceStorage();
}
