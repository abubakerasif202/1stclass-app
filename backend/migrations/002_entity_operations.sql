BEGIN;

CREATE TABLE IF NOT EXISTS idempotency_records (
  scope text PRIMARY KEY,
  request_fingerprint text NOT NULL,
  status_code integer,
  response jsonb,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((status_code IS NULL) = (response IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_idempotency_records_expiry ON idempotency_records (expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_latest_location_per_driver
  ON transport_entities (entity_id)
  WHERE entity_type = 'latestLocations';

INSERT INTO schema_migrations(version) VALUES ('002_entity_operations')
ON CONFLICT (version) DO NOTHING;

COMMIT;
