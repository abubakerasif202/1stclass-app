# TMS sync contract — what the app assumes, and what the backend must confirm

**Status: no live TMS API exists.** Everything in this document is the Android app's *proposal*.
Nothing here has been agreed with the 1st Class Express backend team, and nothing in the app
depends on it being right — the transport is a swappable seam.

At runtime today the app has no endpoint configured. In that state:

- every driver action is saved locally and queued,
- every queued operation stays `PENDING`,
- the UI says "Sync unavailable",
- **nothing is ever reported as synced.**

That last point is the invariant this whole phase protects. A queued operation moves to `SYNCED`
only on a 2xx response from a real server.

---

## 1. Configuration

`TMS_BASE_URL` is supplied at build time from `local.properties` or the environment (CI secret).
It is never committed.

```properties
# local.properties — git-ignored
TMS_BASE_URL=https://tms.example.com/api/
# Optional: point debug builds somewhere else (may be http:// for a local dev host only)
TMS_BASE_URL_DEBUG=http://10.0.2.2:8080/
```

Rules enforced in code:

| Build | Value | Result |
|---|---|---|
| any | empty | Local/offline mode. No requests are attempted. |
| release | `http://…` | **Build fails.** |
| debug | `http://localhost`, `127.0.0.1`, `10.0.2.2`, `10.0.3.2` | Allowed. |
| debug | `http://` anything else | Rejected at runtime and blocked by the network security config. |
| any | `https://…` | Used. |

---

## 2. Idempotency — the single most important thing to agree

Every mutating request carries:

```
X-Idempotency-Key: <SyncOperation.id>
```

The key is a UUID generated **once**, when the driver's action is written to the local database,
and reused **verbatim on every retry, forever**. It survives app restart, process death and
reinstall-from-backup.

The server must:

- treat a repeated key as "already applied" and return **2xx**, not 409,
- never apply the same key twice.

Without this, a response lost on a patchy mobile connection becomes a duplicated proof of
delivery or a duplicated damage claim. If the TMS uses a different mechanism, tell us what it is —
the app can send whatever header or body field the contract defines, but it must be able to send
*something* stable.

---

## 3. Endpoints the app currently assumes

All provisional. See `TmsApi.kt`.

| Operation | Method & path |
|---|---|
| Sign in | `POST v1/driver/auth` |
| Job status change | `POST v1/driver/jobs/{jobId}/status` |
| Shift event | `POST v1/driver/shifts/{shiftId}/events` |
| Pre-start inspection | `POST v1/driver/shifts/{shiftId}/inspection` |
| Freight exception | `POST v1/driver/exceptions` |
| Location points (batch-capable) | `POST v1/driver/locations` |
| Evidence upload (multipart) | `POST v1/driver/evidence` |
| Evidence delete | `DELETE v1/driver/evidence/{evidenceId}` |
| Assigned jobs | `GET v1/driver/jobs?driverId=…` |

---

## 4. Response handling

| Status | App behaviour |
|---|---|
| 2xx | Marked `SYNCED`. The only path to that state. |
| 401 / 403 | Stored token cleared, run stops, **all local data and queued work preserved**, driver asked to sign in again. |
| 408, 425, 429, 5xx | Retryable. Exponential backoff from 30s. Gives up after 10 attempts and parks the operation as `FAILED` for support. |
| other 4xx | Permanent. Operation marked `FAILED`, kept and shown on the sync diagnostics screen. Local data and evidence files untouched. |
| network / TLS / timeout | Retryable. |

A failed sync **never** deletes a job, an inspection, a signature or a photo.

---

## 5. Questions the backend team needs to answer

1. **Idempotency.** Is `X-Idempotency-Key` acceptable? If not, what is the mechanism?
2. **Authentication.** Does login return a bearer token? What is its lifetime? Is there a refresh
   endpoint? *The app does not implement refresh — we will not invent an endpoint that may not
   exist. Today an expired token means the driver signs in again.*
3. **Evidence upload.** Multipart, or pre-signed upload URLs? If pre-signed: what issues the URL,
   what is its TTL, and does metadata go with the file or separately? The app streams from disk
   either way; only the transport class changes.
4. **Evidence identity.** Are the app's client-generated evidence UUIDs acceptable as the
   server-side identifier, or does the TMS assign its own? If the latter, the app needs the
   assigned id in the upload response so exceptions can reference it.
5. **Job refresh.** Does `GET jobs` return a per-job `updatedAt`? The merge policy cannot protect
   driver work without it. What is its clock and its precision?
6. **Job identity.** Are job ids stable and globally unique, or scoped to a depot/day?
7. **Status vocabulary.** Does the TMS accept the app's `JobStatus` names (`ASSIGNED`,
   `AT_PICKUP`, `PICKED_UP`, `EN_ROUTE_DELIVERY`, `AT_DELIVERY`, `COMPLETED`, `ISSUE`) or does it
   need its own codes?
8. **Location volume.** Acceptable batch size and posting frequency? The app currently queues one
   durable operation per point; the transport already accepts a batch.
9. **Inspection model.** Does the TMS want the whole inspection per event, or per-item deltas?
10. **Freight exception reasons.** Does the TMS accept the app's ten reason codes?
11. **Conflict resolution.** When the server and a driver disagree, who wins, and how should the
    app surface it? The current policy is deliberately conservative — see §6.
12. **TLS.** Which CA issues the TMS certificate, and is certificate pinning wanted? Pinning is
    intentionally *not* implemented against a certificate we have not seen.

---

## 6. Job merge policy (implemented, conservative)

`JobMergePolicy` decides per job:

- job not on device → **insert**
- device has queued mutations and statuses disagree → **conflict**, local wins
- device is at a driver-completed status (`PICKED_UP`, `EN_ROUTE_DELIVERY`, `AT_DELIVERY`,
  `COMPLETED`) and the server is not → **conflict**, local wins
- server `updatedAt` not newer → **keep local**
- otherwise → **update**

The principle: a stale `GET` must never erase work a driver has physically done. Automatic
resolution of genuine conflicts is out of scope until question 11 is answered.

---

## 7. Payload versioning

`sync_operations.payloadVersion` defaults to `1`; rows written before the column existed read as
`1` after migration. Payloads are hydrated from the local database at send time rather than from
the stored JSON, so an operation queued by an older app version still produces a complete, current
payload after an upgrade. Bump the version only if the *meaning* of a queued operation changes.

---

## 8. What is deliberately not built

- No refresh-token flow (question 2).
- No live job download wired into the UI — `RemoteJobDataSource` and the merge policy exist and are
  tested, but the runtime implementation is `UnconfiguredRemoteJobDataSource`.
- No certificate pinning (question 12).
- No location batching in the processor (the transport supports it; question 8).
- No fake success anywhere in the shipping code. The only controllable transport lives in the test
  source set.
