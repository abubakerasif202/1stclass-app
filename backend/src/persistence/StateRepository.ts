export type EntityCollection = Record<string, unknown>;

export type TransportEntityType =
  | 'jobs' | 'drivers' | 'vehicles' | 'latestLocations' | 'telemetry'
  | 'incidents' | 'vehicleDefects' | 'messages' | 'auditLogs' | 'dispatchUsers'
  | 'refreshSessions' | 'deviceRegistrations' | 'evidenceMetadata' | 'appConfig';

export interface EntityWriteOptions {
  expectedRevision?: number;
}

export interface IdempotencyEntry {
  requestFingerprint: string;
  statusCode: number | null;
  response: unknown | null;
  expiresAt: number;
}

export interface PersistedTransportState {
  jobs: EntityCollection;
  drivers: EntityCollection;
  vehicles: EntityCollection;
  latestLocations: EntityCollection;
  telemetry: EntityCollection;
  incidents: EntityCollection;
  vehicleDefects: EntityCollection;
  messages: EntityCollection;
  auditLogs: EntityCollection;
  dispatchUsers: EntityCollection;
  idempotency: EntityCollection;
  refreshSessions: EntityCollection;
  deviceRegistrations: EntityCollection;
  evidenceMetadata: EntityCollection;
  appConfig: unknown;
}

export interface StateRepository {
  readonly kind: 'file' | 'postgres';
  load(): Promise<PersistedTransportState | null>;
  save(state: PersistedTransportState): Promise<void>;
  health(): Promise<boolean>;
  close(): Promise<void>;
}
