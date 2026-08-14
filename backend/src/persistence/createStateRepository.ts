import { FileStateRepository } from './FileStateRepository';
import { PostgresStateRepository } from './PostgresStateRepository';
import { StateRepository } from './StateRepository';

export function createStateRepository(): StateRepository {
  const databaseUrl = process.env.DATABASE_URL;
  if (databaseUrl) return new PostgresStateRepository(databaseUrl);
  if (process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging') {
    throw new Error('DATABASE_URL is required outside development and tests');
  }
  return new FileStateRepository();
}
