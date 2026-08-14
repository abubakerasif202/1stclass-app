# Production Readiness

Last audited: 2026-08-15 (Australia/Sydney)

This checklist is a release gate, not a claim that the pilot has run. A box may be checked only when the referenced evidence was captured against the deployed staging system or physical pilot phone.

## Current gate decision

**LOCAL REPOSITORY RELEASE GATE VERIFIED — do not distribute this build as an enterprise pilot until the external staging and signing gates below are complete.**

Windows builds, Android unit tests/lint, backend HTTP/security tests, dispatcher contract and edit-job tests, PostgreSQL 17 restart/two-instance/concurrency checks, and MinIO restart checks passed on 2026-08-15. A Huawei VOG-L29 installed and launched the rebuilt debug APK without an immediate application crash; the earlier debug login and pickup-workflow smoke remains valid. Production/staging hosting, production provisioning, Android signing, Firebase project configuration/physical delivery, and the complete device matrix remain unverified.

## Build and Android release

- [x] Android `clean test lint assembleDebug` passes on Eclipse Temurin JDK 21.
- [ ] Android `assembleRelease bundleRelease` passes with the official signing key.
- [x] Stable Android application ID is `au.com.firstclassexpress.driver`.
- [x] Kotlin/Java namespace migration from `com.example` is completed; active `app/` source has zero matches and the rebuilt APK resolves `au.com.firstclassexpress.driver/.MainActivity`.
- [x] Release cleartext traffic is disabled.
- [x] Release signing reads only `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`; keystores and output artifacts are ignored by Git.
- [ ] Pilot keystore is generated, backed up in two access-controlled locations, and recovery ownership is recorded.
- [ ] APK and AAB are built with `scripts/build-pilot.ps1`; APK/AAB signature and SHA-256 output is archived with the release record.
- [ ] Full fresh-install, upgrade, process-kill, reboot, camera, and GPS matrix passes on HUAWEI VOG-L29 (Android 10 / SDK 29). Debug install, launch, login, and pickup workflow entry passed.

Generate a pilot key once from a secure Windows workstation (replace the alias if required):

```powershell
keytool -genkeypair -v -keystore C:\Secure\1ce-driver-pilot.jks -alias 1ce-driver-pilot -keyalg RSA -keysize 4096 -validity 3650
$env:KEYSTORE_PATH = 'C:\Secure\1ce-driver-pilot.jks'
$env:KEY_ALIAS = '1ce-driver-pilot'
$env:TMS_BASE_URL = 'https://<real-staging-api-host>'
# Populate KEYSTORE_PASSWORD and KEY_PASSWORD from the approved secret manager
# into this process without printing or persisting them.
.\scripts\build-pilot.ps1
```

Never place those values in `local.properties`, tracked `.env` files, CI output, chat, or tickets. Store them in the organisation password manager/CI secret store.

## Staging deployment

- [ ] `STAGING_API_URL`: **not provisioned or verified**.
- [ ] `STAGING_DISPATCHER_URL`: **not provisioned or verified**.
- [ ] API and dispatcher resolve publicly, use valid TLS, redirect HTTP to HTTPS, and pass real HTTP health/browser checks.
- [ ] Dispatcher is built with the exact staging API origin and secure SSE URL.
- [ ] Deployment platform, environment owner, rollback revision, and access policy are recorded.

The strings `https://staging-api.1stclassexpress.com.au` and related Docker defaults are configuration placeholders until DNS and HTTP verification succeed.

## Database, media, push, and health

- [x] Production routes use entity-level PostgreSQL repository operations; process-local maps/snapshots are limited to development compatibility and are not production authority.
- [x] Migrations `001_initial` and `002_entity_operations` apply to disposable PostgreSQL 17; operational records and idempotency data survive adapter restart.
- [x] Two independent backend repository instances observe committed jobs and latest locations without restart. Stale revisions, database-native duplicate idempotency, and transaction rollback tests pass.
- [x] The S3-compatible adapter persists synthetic JPEG and PNG objects across a MinIO service restart.
- [ ] Upload authorization, job/driver ownership, JPEG/PNG magic-byte validation, 10 MiB limit, retrieval authorization, malware policy, and deletion audit are all verified. Automated coverage currently proves authorization, ownership, signatures, MIME, empty, oversized, JPEG, and PNG cases; malware scanning/deletion audit remain open.
- [ ] Firebase Admin and the Android FCM client are implemented with environment-only/project-local configuration, durable pending-token registration, token replacement, safe notification routing, batching, and invalid-token cleanup. Real Firebase configuration/credentials and physical-device delivery/deep-link cases remain externally blocked.
- [ ] Public liveness reveals no internals. Authenticated/internal readiness checks database, storage, and push dependencies.

## Security gates

- [x] Production startup does not seed users, plaintext credentials, fake tokens, customer-like PII, or simulated coordinates; synthetic fixtures are restricted to explicit development/test execution.
- [x] Dispatcher passwords and driver PINs are hashed; login and refresh endpoints are rate-limited.
- [x] HTTP regression coverage proves unauthenticated 401, role 403, cross-driver job/telemetry denial, evidence denial, and restricted audit access.
- [x] Refresh-token signature, explicit type, expiry, subject, disabled-user handling, and rotation are validated.
- [x] Production/staging require a high-entropy `JWT_SECRET`; no fallback secret exists.
- [x] Production requires explicit non-wildcard CORS origins.
- [x] Dispatcher authentication uses secure HttpOnly/SameSite cookies and memory-only CSRF state, not persistent Web Storage.
- [x] Current-tree and Git-history secret scans found no confirmed live credential; placeholders, test fixtures, field names, and false positives were classified.
- [ ] Android exported components, deep-link allowlist, immutable `PendingIntent`s, encrypted token storage, backups, release logs, and network security are reviewed.

## Backups and retention

Retention periods must be approved by operations/legal; this document does not invent them.

- Database: encrypted automated backups, configurable retention, point-in-time recovery where supported, separate-account copy, quarterly restore drill, and named owner.
- Media: object versioning, encryption, lifecycle rules driven by approved retention, legal-hold exception, deletion audit, and restore test.
- GPS: configurable retention and access logging; collect only what operations requires.
- Signing key: two encrypted access-controlled backups; losing it may prevent trusted upgrades.

## Rollback

- Backend: redeploy the last known-good immutable image; keep backward-compatible migrations during the pilot. For a breaking migration, stop writes, restore the verified pre-migration backup, then deploy the matching image.
- Dispatcher: redeploy the prior immutable static bundle and purge/invalidate the CDN cache.
- Android: managed distribution must retain the prior signed APK. Android cannot downgrade `versionCode` normally; pause rollout or publish a fixed build with a higher code signed by the same key.
- Remote config: disable the affected feature flag first when safe; record actor, reason, and timestamp.

## Evidence required to approve pilot

- [ ] Build logs and hashes
- [ ] TLS/HTTP transcripts and deployment revisions
- [ ] Database restart/restore and object-storage retrieval evidence
- [ ] Physical-device matrix from `PILOT-RUNBOOK.md`
- [ ] Browser console/network capture for dispatcher and realtime recovery
- [ ] Security review with zero open critical/high findings
- [ ] Named rollback decision-maker and support contact
