BEGIN;

CREATE TABLE IF NOT EXISTS schema_migrations (
  version text PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transport_entities (
  entity_type text NOT NULL,
  entity_id text NOT NULL,
  status text,
  driver_id text,
  vehicle_id text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  recorded_at timestamptz,
  expires_at timestamptz,
  data jsonb NOT NULL,
  PRIMARY KEY (entity_type, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_entities_type_status
  ON transport_entities (entity_type, status);
CREATE INDEX IF NOT EXISTS idx_entities_jobs_driver
  ON transport_entities (driver_id) WHERE entity_type = 'jobs';
CREATE INDEX IF NOT EXISTS idx_entities_jobs_vehicle
  ON transport_entities (vehicle_id) WHERE entity_type = 'jobs';
CREATE INDEX IF NOT EXISTS idx_entities_type_updated
  ON transport_entities (entity_type, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_entities_telemetry_driver_recorded
  ON transport_entities (driver_id, recorded_at DESC) WHERE entity_type = 'telemetry';
CREATE INDEX IF NOT EXISTS idx_entities_messages_driver
  ON transport_entities (driver_id, updated_at DESC) WHERE entity_type = 'messages';
CREATE INDEX IF NOT EXISTS idx_entities_incidents_status
  ON transport_entities (status, updated_at DESC) WHERE entity_type = 'incidents';
CREATE INDEX IF NOT EXISTS idx_entities_audit_timestamp
  ON transport_entities (updated_at DESC) WHERE entity_type = 'auditLogs';
CREATE INDEX IF NOT EXISTS idx_entities_idempotency_expiry
  ON transport_entities (expires_at) WHERE entity_type = 'idempotency';

CREATE TABLE IF NOT EXISTS auth_credentials (
  user_id text PRIMARY KEY,
  driver_id text,
  email text UNIQUE,
  role text NOT NULL,
  credential_hash text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS refresh_sessions (
  session_id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES auth_credentials(user_id) ON DELETE CASCADE,
  token_hash text NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_active
  ON refresh_sessions (user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS evidence_metadata (
  evidence_id text PRIMARY KEY,
  job_id text,
  driver_id text NOT NULL,
  evidence_type text NOT NULL,
  content_type text NOT NULL,
  size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
  sha256 text NOT NULL,
  storage_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_evidence_job ON evidence_metadata (job_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_evidence_driver ON evidence_metadata (driver_id, created_at DESC);

CREATE TABLE IF NOT EXISTS device_registrations (
  device_id text PRIMARY KEY,
  driver_id text NOT NULL,
  platform text NOT NULL,
  app_version text,
  push_token text UNIQUE,
  push_enabled boolean NOT NULL DEFAULT true,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_devices_driver ON device_registrations (driver_id, updated_at DESC);

INSERT INTO schema_migrations(version) VALUES ('001_initial')
ON CONFLICT (version) DO NOTHING;

COMMIT;
