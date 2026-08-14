# Enterprise Pilot Runbook

Use only synthetic staging customers, recipients, phone numbers, addresses, photos, and signatures. Record timestamps in UTC and local time. Never test against production unless a separately approved production test plan exists.

## Preconditions

1. Confirm `docs/PRODUCTION-READINESS.md` has no open release blocker.
2. Record API URL, dispatcher URL, backend/dispatcher revisions, APK SHA-256, app version/code, package ID, phone model, Android version, serial, carrier/network, and test operators.
3. Verify API health and TLS from a network outside the development machine:

```powershell
curl.exe -fsS -D - https://<staging-api>/v1/health
curl.exe -fsS -I https://<staging-dispatcher>/
```

4. Confirm the test driver/device and vehicle are isolated staging records and FCM token storage is empty before registration.

## Install and startup

```powershell
adb devices -l
adb -s <serial> uninstall au.com.firstclassexpress.driver
adb -s <serial> install "<signed-pilot-apk>"
adb -s <serial> shell cmd package resolve-activity --brief au.com.firstclassexpress.driver
```

Record launch, runtime permissions, login success/failure, Room creation, navigation, GPS service, camera, notification permission, FCM token upload, logout/restart, token refresh/expiry, backend restart, and network failure. Redact tokens and PII from captured logs.

For upgrade testing, first install the exact previous signed pilot, create an active job and pending offline operations, then install the new APK with `adb install -r`. Verify Room migrations, DataStore/session policy, active job, evidence, and pending queue before syncing.

## Dispatcher to driver journey

1. Open the deployed dispatcher in two browser sessions. Capture console/network errors and the displayed connection state.
2. Create one synthetic job and assign it to the pilot driver.
3. Record dispatcher-created, backend-persisted, push-sent, device-received, and job-visible timestamps.
4. Send `NEW_JOB`, `JOB_UPDATED`, `JOB_CANCELLED`, `DISPATCH_MESSAGE`, and `URGENT_NOTICE` with the app foreground, background, locked, and process-terminated. Verify the exact deep-link destination in every case.
5. Confirm token generation, driver/device association, token refresh replacement, invalid-token cleanup, and absence of duplicate active tokens.

## GPS and lifecycle

Outdoors, start the shift and walk with the active job. Record at least three genuine fused-location samples: coordinates, recorded/received timestamps, accuracy, speed, battery, network, and dispatcher freshness. Stop updates or disconnect and confirm the dispatcher changes to `STALE`/`OFFLINE` with a last-updated age.

Perform and verify in dispatcher after each transition:

```text
ASSIGNED -> ACCEPTED -> IN_PROGRESS -> AT_PICKUP -> PICKED_UP
-> EN_ROUTE_DELIVERY -> AT_DELIVERY -> POD -> COMPLETED
```

Capture freight, pickup, incident, and POD photos with the real camera. Record compressed JPEG sizes. Capture a synthetic signature and POD with recipient, timestamp, GPS, notes, photo, and signature; compare the dispatcher record field-by-field.

## Mandatory offline recovery

1. During the active job, enable airplane mode.
2. Perform arrival, photo, pickup completion, delivery, signature, POD, and completion.
3. Record Room rows/diagnostic queue counts without altering them.
4. Kill the process, relaunch, and verify job, shift, evidence files, and all pending operations remain.
5. Reconnect and verify automatic ordered sync. Confirm one server record per idempotency key and no duplicate lifecycle, POD, incident, telemetry, or message records.
6. Repeat with a real phone reboot. Record what resumed automatically and what required user action.

## Conflict and recovery tests

- Restart staging API during active work; verify visible connection loss, durable queueing, reconnect, and ordered sync.
- Interrupt dispatcher SSE; verify reconnect plus authoritative state reload, correct stale status, and no duplicates.
- Edit the same job in two dispatcher sessions; the stale revision must receive `409` and a clear refresh/reapply message.
- Reassign Driver A to Driver B; verify revision, both device updates, audit event, and one active assignment.
- Cancel an active job; verify audit, notification, non-actionable local state, and appropriate preservation of evidence.
- Submit/acknowledge/resolve an incident with photo; verify driver, job, vehicle, severity, timestamp, GPS, and authorization.
- Submit a pre-start defect and change its status; verify dispatcher alert and immutable audit trail.

## Measurements

For a two-hour run, record start/end battery, exact duration, GPS mode, screen-on time, network type, and location update count. Calculate percentage points per hour; do not extrapolate beyond the observed device/run. Capture Android per-app network usage before/after and separate telemetry/API/media bytes where server metrics permit.

## Troubleshooting and diagnostics

- App cannot connect: verify public DNS/TLS from the phone network, exact release base URL, backend health, device clock, and authorization response.
- No push: verify notification permission, current FCM token, backend device association, Firebase delivery result, background restrictions, and invalid-token cleanup.
- Queue not draining: retain Room/evidence data, capture redacted WorkManager/logcat diagnostics, idempotency key and HTTP status, then stop repeated manual taps.
- Dispatcher stale: verify SSE status, browser network/console, API authoritative state, and location recorded/received timestamps.
- Never clear app data, uninstall, delete uploads, or retry a migration until pending evidence is exported or its loss is explicitly accepted.

## Test record

For every item report exactly one of: `IMPLEMENTED`, `AUTOMATED TESTED`, `BROWSER TESTED`, `PHYSICAL DEVICE TESTED`, `STAGING TESTED`, `PRODUCTION TESTED`, `NOT TESTED`, or `BLOCKED`. Attach evidence location, operator, timestamp, expected result, actual result, and issue ID. Production testing must remain `NOT TESTED` unless actually performed against production under approval.
