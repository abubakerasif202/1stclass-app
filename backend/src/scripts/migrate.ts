import { readFile, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';

async function main() {
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
  const { Pool } = require('pg') as typeof import('pg');
  const pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
  try {
    const migrationDirectory = resolve('migrations');
    const files = (await readdir(migrationDirectory)).filter(file => /^\d+_.+\.sql$/.test(file)).sort();
    const first = files.shift();
    if (!first) throw new Error('No migrations found');
    await pool.query(await readFile(resolve(migrationDirectory, first), 'utf8'));
    const applied = await pool.query('SELECT version FROM schema_migrations');
    const versions = new Set(applied.rows.map((row: { version: string }) => row.version));
    for (const file of files) {
      const version = file.replace(/\.sql$/, '');
      if (versions.has(version)) continue;
      await pool.query(await readFile(resolve(migrationDirectory, file), 'utf8'));
      console.log(`Applied migration ${version}`);
    }
  } finally {
    await pool.end();
  }
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : 'Migration failed');
  process.exitCode = 1;
});
