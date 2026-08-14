import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { TransportDatabase } from '../db';
import { FileStateRepository } from '../persistence/FileStateRepository';
import { LocalDevelopmentEvidenceStorage } from '../storage/LocalDevelopmentEvidenceStorage';

describe('durable development adapters', () => {
  test('operational state and evidence survive adapter restart', async () => {
    const root = await mkdtemp(join(tmpdir(), 'tms-persistence-'));
    try {
      const statePath = join(root, 'transport.json');
      const first = new TransportDatabase();
      await first.initialize(new FileStateRepository(statePath));
      first.seed();
      const job = first.jobs.get('job-101')!;
      job.status = 'AT_DELIVERY';
      first.incidents.set('restart-incident', { ...first.incidents.values().next().value!, id: 'restart-incident' });
      first.messages.push({ ...first.messages[0], id: 'restart-message' });
      first.idempotencyKeys.set('restart-key', {
        scope: 'restart-key', requestFingerprint: 'abc', statusCode: 201,
        response: { success: true }, createdAt: Date.now(), expiresAt: Date.now() + 60000
      });
      await first.persist();
      await first.close();

      const second = new TransportDatabase();
      await second.initialize(new FileStateRepository(statePath));
      expect(second.jobs.get('job-101')?.status).toBe('AT_DELIVERY');
      expect(second.drivers.has('DRV-101')).toBe(true);
      expect(second.vehicles.has('VH-101')).toBe(true);
      expect(second.incidents.has('restart-incident')).toBe(true);
      expect(second.messages.some(message => message.id === 'restart-message')).toBe(true);
      expect(second.auditLogs.length).toBeGreaterThan(0);
      expect(second.idempotencyKeys.has('restart-key')).toBe(true);
      expect(second.latestLocations.has('DRV-101')).toBe(true);

      const storage = new LocalDevelopmentEvidenceStorage(join(root, 'evidence'));
      await storage.put('DRV-101/restart.png', 'image/png', Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
      const restartedStorage = new LocalDevelopmentEvidenceStorage(join(root, 'evidence'));
      const stored = await restartedStorage.get('DRV-101/restart.png');
      expect(stored.sizeBytes).toBe(8);
      const chunks: Buffer[] = [];
      for await (const chunk of stored.body) chunks.push(Buffer.from(chunk));
      expect(Buffer.concat(chunks)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
});
