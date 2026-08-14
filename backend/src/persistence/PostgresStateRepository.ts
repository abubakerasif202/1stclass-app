import type { Pool as PoolType, PoolClient } from 'pg';
import { AsyncLocalStorage } from 'node:async_hooks';
import { EntityCollection, PersistedTransportState, StateRepository } from './StateRepository';
import { EntityWriteOptions, IdempotencyEntry, TransportEntityType } from './StateRepository';

const COLLECTIONS = [
  'jobs', 'drivers', 'vehicles', 'latestLocations', 'telemetry', 'incidents',
  'vehicleDefects', 'messages', 'auditLogs', 'dispatchUsers', 'idempotency',
  'refreshSessions', 'deviceRegistrations', 'evidenceMetadata'
] as const;

type CollectionName = typeof COLLECTIONS[number];

function indexFields(type: CollectionName, value: any) {
  const timestamp = value.serverUpdatedAt ?? value.updatedAt ?? value.timestamp ?? value.sentAt ??
    value.reportedAt ?? value.recordedAt ?? Date.now();
  return {
    status: value.status ?? null,
    driverId: value.assignedDriverId ?? value.driverId ?? null,
    vehicleId: value.assignedVehicleId ?? value.vehicleId ?? null,
    updatedAt: new Date(timestamp),
    recordedAt: value.recordedAt ? new Date(value.recordedAt) : null,
    expiresAt: value.expiresAt ? new Date(value.expiresAt) : null
  };
}

export class PostgresStateRepository implements StateRepository {
  readonly kind = 'postgres' as const;
  private readonly pool: PoolType;
  private readonly transactionClient = new AsyncLocalStorage<PoolClient>();

  constructor(connectionString: string) {
    const { Pool } = require('pg') as typeof import('pg');
    this.pool = new Pool({ connectionString, max: Number(process.env.DB_POOL_SIZE || 10) });
  }

  async load(): Promise<PersistedTransportState | null> {
    const result = await this.pool.query('SELECT entity_type, entity_id, data FROM transport_entities');
    if (result.rowCount === 0) return null;
    const state: any = Object.fromEntries(COLLECTIONS.map(name => [name, {}]));
    state.appConfig = null;
    for (const row of result.rows) {
      if (row.entity_type === 'appConfig') state.appConfig = row.data;
      else if (COLLECTIONS.includes(row.entity_type)) state[row.entity_type][row.entity_id] = row.data;
    }
    return state as PersistedTransportState;
  }

  async save(state: PersistedTransportState): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      for (const collection of COLLECTIONS) {
        await this.replaceCollection(client, collection, state[collection]);
      }
      await client.query(
        `INSERT INTO transport_entities(entity_type, entity_id, data)
         VALUES ('appConfig', 'singleton', $1::jsonb)
         ON CONFLICT(entity_type, entity_id) DO UPDATE SET data = EXCLUDED.data, updated_at = now()`,
        [JSON.stringify(state.appConfig)]
      );
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  private async replaceCollection(client: PoolClient, type: CollectionName, values: EntityCollection) {
    for (const [id, value] of Object.entries(values)) {
      const indexed = indexFields(type, value);
      await client.query(
        `INSERT INTO transport_entities(
           entity_type, entity_id, status, driver_id, vehicle_id, updated_at, recorded_at, expires_at, data
         ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb)
         ON CONFLICT(entity_type, entity_id) DO UPDATE SET
           status=EXCLUDED.status, driver_id=EXCLUDED.driver_id, vehicle_id=EXCLUDED.vehicle_id,
           updated_at=EXCLUDED.updated_at, recorded_at=EXCLUDED.recorded_at,
           expires_at=EXCLUDED.expires_at, data=EXCLUDED.data
         WHERE transport_entities.data = EXCLUDED.data
            OR NOT (transport_entities.data ? 'revision')
            OR (EXCLUDED.data->>'revision')::integer > (transport_entities.data->>'revision')::integer`,
        [type, id, indexed.status, indexed.driverId, indexed.vehicleId, indexed.updatedAt,
          indexed.recordedAt, indexed.expiresAt, JSON.stringify(value)]
      );
    }
  }

  async health(): Promise<boolean> {
    const result = await this.pool.query('SELECT 1 AS ok');
    return result.rows[0]?.ok === 1;
  }

  async close(): Promise<void> { await this.pool.end(); }

  private async query<T = any>(text: string, values: unknown[] = []) {
    const client = this.transactionClient.getStore();
    return (client ? client.query(text, values) : this.pool.query(text, values)) as Promise<{ rows: T[]; rowCount: number | null }>;
  }

  async transaction<T>(work: () => Promise<T>): Promise<T> {
    if (this.transactionClient.getStore()) return work();
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await this.transactionClient.run(client, work);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async getEntity<T>(type: TransportEntityType, id: string): Promise<T | undefined> {
    const result = await this.query<{ data: T }>(
      'SELECT data FROM transport_entities WHERE entity_type = $1 AND entity_id = $2', [type, id]
    );
    return result.rows[0]?.data;
  }

  async listEntities<T>(type: TransportEntityType): Promise<T[]> {
    const result = await this.query<{ data: T }>(
      'SELECT data FROM transport_entities WHERE entity_type = $1 ORDER BY updated_at DESC, entity_id', [type]
    );
    return result.rows.map(row => row.data);
  }

  async putEntity<T extends object>(
    type: TransportEntityType, id: string, value: T, options: EntityWriteOptions = {}
  ): Promise<boolean> {
    const indexed = indexFields(type as CollectionName, value);
    if (options.expectedRevision === undefined) {
      await this.query(
        `INSERT INTO transport_entities(
           entity_type, entity_id, status, driver_id, vehicle_id, updated_at, recorded_at, expires_at, data
         ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb)
         ON CONFLICT(entity_type, entity_id) DO UPDATE SET
           status=EXCLUDED.status, driver_id=EXCLUDED.driver_id, vehicle_id=EXCLUDED.vehicle_id,
           updated_at=EXCLUDED.updated_at, recorded_at=EXCLUDED.recorded_at,
           expires_at=EXCLUDED.expires_at, data=EXCLUDED.data`,
        [type, id, indexed.status, indexed.driverId, indexed.vehicleId, indexed.updatedAt,
          indexed.recordedAt, indexed.expiresAt, JSON.stringify(value)]
      );
      return true;
    }
    const result = await this.query(
      `UPDATE transport_entities SET
         status=$3, driver_id=$4, vehicle_id=$5, updated_at=$6, recorded_at=$7, expires_at=$8, data=$9::jsonb
       WHERE entity_type=$1 AND entity_id=$2
         AND COALESCE((data->>'revision')::integer, 0) = $10`,
      [type, id, indexed.status, indexed.driverId, indexed.vehicleId, indexed.updatedAt,
        indexed.recordedAt, indexed.expiresAt, JSON.stringify(value), options.expectedRevision]
    );
    return result.rowCount === 1;
  }

  async deleteEntity(type: TransportEntityType, id: string): Promise<void> {
    await this.query('DELETE FROM transport_entities WHERE entity_type = $1 AND entity_id = $2', [type, id]);
  }

  async readIdempotency(scope: string): Promise<IdempotencyEntry | undefined> {
    await this.query('DELETE FROM idempotency_records WHERE expires_at <= now()');
    const result = await this.query<{
      request_fingerprint: string; status_code: number | null; response: unknown | null; expires_at: Date;
    }>('SELECT request_fingerprint, status_code, response, expires_at FROM idempotency_records WHERE scope = $1', [scope]);
    const row = result.rows[0];
    return row && {
      requestFingerprint: row.request_fingerprint,
      statusCode: row.status_code,
      response: row.response,
      expiresAt: row.expires_at.getTime()
    };
  }

  async reserveIdempotency(scope: string, fingerprint: string, expiresAt: number): Promise<boolean> {
    const result = await this.query(
      `INSERT INTO idempotency_records(scope, request_fingerprint, expires_at)
       VALUES ($1, $2, to_timestamp($3 / 1000.0)) ON CONFLICT(scope) DO NOTHING`,
      [scope, fingerprint, expiresAt]
    );
    return result.rowCount === 1;
  }

  async completeIdempotency(scope: string, statusCode: number, response: unknown): Promise<void> {
    await this.query(
      'UPDATE idempotency_records SET status_code = $2, response = $3::jsonb WHERE scope = $1',
      [scope, statusCode, JSON.stringify(response)]
    );
  }

  async pruneTelemetry(retentionDays: number, maximumPoints: number): Promise<void> {
    if (retentionDays > 0) {
      await this.query(
        `DELETE FROM transport_entities
         WHERE entity_type = 'telemetry' AND recorded_at < now() - ($1 * interval '1 day')`,
        [retentionDays]
      );
    }
    if (maximumPoints > 0) {
      await this.query(
        `DELETE FROM transport_entities WHERE entity_type = 'telemetry' AND entity_id IN (
           SELECT entity_id FROM transport_entities WHERE entity_type = 'telemetry'
           ORDER BY recorded_at DESC NULLS LAST, entity_id OFFSET $1
         )`,
        [maximumPoints]
      );
    }
  }
}
