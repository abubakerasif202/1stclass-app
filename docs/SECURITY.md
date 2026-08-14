# TMS Security Notes

- Public routes are limited to liveness, authentication, and non-sensitive mobile app configuration. Business, telemetry, evidence, SSE, and audit routes require authentication.
- Driver tokens carry a driver subject. Server middleware rejects cross-driver job mutation, telemetry, profile, vehicle-defect, message, incident, and evidence access. Dispatch roles are server-issued and cannot be changed in the browser.
- Dispatcher passwords and driver PINs are stored only as salted scrypt hashes. Login responses and driver APIs explicitly omit hashes. Authentication endpoints use targeted per-IP/per-subject limits; telemetry is not globally throttled by that policy.
- Refresh JWTs are signed with issuer/audience/type/expiry/session claims. Only a matching, active, persisted session hash may rotate. Replayed, disabled-user, expired, or revoked sessions are rejected.
- Dispatcher JWTs are Secure/HttpOnly/SameSite cookies. State-changing cookie requests require the matching CSRF value. Exact configured CORS origins and credentialed requests are enforced.
- Evidence upload accepts one file up to 10 MiB, verifies both declared MIME and JPEG/PNG magic bytes, calculates SHA-256, uses an opaque private storage key, and records driver/job ownership. Retrieval is authenticated and returns `private, no-store`.
- Telemetry is private operational data. Drivers may submit only their own identity and assigned job relationship; dispatcher access is role restricted. Retention is configurable and must be approved operationally.
- Idempotency records are actor/method/route scoped and request-fingerprinted. Audit records include actor, action, entity, before/after where supplied, timestamp, and request correlation ID.
- Client errors are structured and production responses do not contain stack traces. Logs contain request IDs and error classes, not passwords, PINs, JWTs, authorization headers, signatures, or customer payloads.
- Secrets come from the deployment secret manager. `.env`, keystores, database state, evidence, and generated artifacts are excluded from version control. Any real historical credential must be rotated; deleting a file is not rotation.
