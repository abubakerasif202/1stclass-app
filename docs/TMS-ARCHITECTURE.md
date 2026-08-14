# TMS Architecture

```text
ANDROID DRIVER APP
        |
        v
   TRANSPORT API
        |
 +------+--------+
 |      |        |
 v      v        v
DB   STORAGE    FCM
 |
 v
DISPATCHER WEB
```

## Runtime boundaries

The Android app is an offline-first client. Room owns pending lifecycle operations, evidence metadata, shifts, and telemetry until the API acknowledges them. WorkManager retries the queue with the same idempotency key after process death or network loss. Acknowledgement is the only point at which an operation becomes synced.

The Transport API owns authorization, lifecycle validation, revisions, audit, durable idempotency, and persistence. Routes use the `TransportDatabase` domain state facade; the facade serializes through `StateRepository`. Development/tests may use the atomic file adapter. Staging and production require `DATABASE_URL` and use `PostgresStateRepository`; startup fails rather than falling back to local memory or a file.

Migration `backend/migrations/001_initial.sql` creates version tracking, durable entity storage, credential/session, evidence, and device-registration tables plus indexes matching dispatcher and telemetry queries. Apply it explicitly with `npm run db:migrate` before deploying a new API image. Migrations are never rolled back automatically; take a verified backup before schema changes and use a forward repair unless the release runbook explicitly approves database restore.

Evidence bytes are separate from operational records. Development uses app-private local files. Staging/production require a private S3-compatible bucket. The database stores evidence ID, job/driver ownership, type, content type, size, SHA-256, private storage key, and creation time. Clients retrieve evidence only through the authenticated API; storage keys and public bucket URLs are not returned.

FCM delivery uses the Firebase HTTP v1 API with service-account credentials supplied by the runtime secret manager. Device registrations are keyed by device ID, tokens are unique, refresh replaces prior ownership, and invalid/unregistered tokens are disabled.

## Authentication and synchronization

Driver access tokens last 15 minutes. Rotating refresh tokens last seven days and are represented by persisted, hashed refresh-session records. Rotation revokes the previous session; logout revokes active driver sessions. Dispatcher access uses a Secure/HttpOnly/SameSite cookie plus a per-session CSRF value. Browser code never persists JWTs.

Idempotency scope is `actor + method + route + key`. A SHA-256 request fingerprint prevents a key from being reused with different content. Successful status/body and expiry are persisted. Job revisions provide optimistic concurrency; stale dispatcher writes return `JOB_REVISION_CONFLICT`.

Latest driver position is maintained separately from historical telemetry. The latest-location query does not scan history. Historical retention is operationally configured with `TELEMETRY_RETENTION_DAYS`; no legal retention period is assumed. `TELEMETRY_MAX_POINTS` provides a configurable safety ceiling.

## Deployment requirements

The API will not start outside development/tests without PostgreSQL, private object storage, FCM configuration, a strong JWT secret, and exact CORS origins. Deployment runs migrations as a separate controlled step, then starts the immutable image. Public liveness is `/health/live`; authenticated readiness is `/health/ready`. The dispatcher is an immutable static build configured with the exact HTTPS API origin.
