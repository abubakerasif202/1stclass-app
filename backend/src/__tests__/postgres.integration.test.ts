import { TransportDatabase } from '../db';
import { PostgresStateRepository } from '../persistence/PostgresStateRepository';

const databaseUrl = process.env.TEST_DATABASE_URL;
const describePostgres = databaseUrl ? describe : describe.skip;

describePostgres('PostgreSQL entity operations integration', () => {
  beforeEach(async () => {
    const { Pool } = require('pg') as typeof import('pg');
    const pool = new Pool({ connectionString: databaseUrl!, max: 1 });
    await pool.query('DELETE FROM idempotency_records');
    await pool.query('DELETE FROM transport_entities');
    await pool.end();
  });

  test('two independent instances read entity writes and latest locations after restart', async () => {
    const first = new TransportDatabase();
    await first.initialize(new PostgresStateRepository(databaseUrl!));
    await first.transaction(async () => {
      await first.put('drivers', 'DRV-1', { id: 'DRV-1', name: 'Driver', active: true, shiftStatus: 'ON_DUTY' });
      await first.put('jobs', 'job-1', { id: 'job-1', reference: 'PG-1', status: 'ASSIGNED', revision: 1, assignedDriverId: 'DRV-1', assignedVehicleId: null, serverUpdatedAt: Date.now() });
      await first.put('latestLocations', 'DRV-1', { driverId: 'DRV-1', vehicleId: null, jobId: 'job-1', latitude: -37.8, longitude: 144.9, recordedAt: Date.now() });
      await first.put('messages', 'msg-1', { id: 'msg-1', driverId: 'DRV-1', content: 'persisted', sentAt: Date.now() });
      await first.put('incidents', 'inc-1', { id: 'inc-1', driverId: 'DRV-1', status: 'OPEN', reportedAt: Date.now() });
    });
    await first.close();

    const restarted = new TransportDatabase();
    await restarted.initialize(new PostgresStateRepository(databaseUrl!));
    expect((await restarted.get<any>('jobs', 'job-1'))?.reference).toBe('PG-1');
    expect((await restarted.get<any>('latestLocations', 'DRV-1'))?.latitude).toBe(-37.8);
    expect((await restarted.get<any>('messages', 'msg-1'))?.content).toBe('persisted');
    expect((await restarted.get<any>('incidents', 'inc-1'))?.status).toBe('OPEN');
    await restarted.close();
  });

  test('concurrent optimistic revisions, idempotency, and rollback are database-native', async () => {
    const writer = new TransportDatabase();
    const competitor = new TransportDatabase();
    await Promise.all([
      writer.initialize(new PostgresStateRepository(databaseUrl!)),
      competitor.initialize(new PostgresStateRepository(databaseUrl!))
    ]);
    await writer.put('jobs', 'job-concurrent', { id: 'job-concurrent', reference: 'PG-CONCURRENT', status: 'ASSIGNED', revision: 1, serverUpdatedAt: Date.now() });
    const [left, right] = await Promise.all([
      writer.get<any>('jobs', 'job-concurrent'), competitor.get<any>('jobs', 'job-concurrent')
    ]);
    left!.revision = 2; left!.reference = 'writer';
    right!.revision = 2; right!.reference = 'competitor';
    const [writerWon, competitorWon] = await Promise.all([
      writer.put('jobs', left!.id, left!, { expectedRevision: 1 }),
      competitor.put('jobs', right!.id, right!, { expectedRevision: 1 })
    ]);
    expect(Number(writerWon) + Number(competitorWon)).toBe(1);

    expect(await writer.reserveIdempotency('driver:POST:/jobs:duplicate', 'body-a', Date.now() + 60_000)).toBe(true);
    expect(await competitor.reserveIdempotency('driver:POST:/jobs:duplicate', 'body-a', Date.now() + 60_000)).toBe(false);
    await writer.completeIdempotency('driver:POST:/jobs:duplicate', 201, { created: true });
    expect((await competitor.readIdempotency('driver:POST:/jobs:duplicate'))?.statusCode).toBe(201);

    await expect(writer.transaction(async () => {
      await writer.put('jobs', 'rolled-back', { id: 'rolled-back', reference: 'NOPE', status: 'ASSIGNED', revision: 1, serverUpdatedAt: Date.now() });
      throw new Error('intentional rollback');
    })).rejects.toThrow('intentional rollback');
    expect(await competitor.get('jobs', 'rolled-back')).toBeUndefined();
    await Promise.all([writer.close(), competitor.close()]);
  });
});
