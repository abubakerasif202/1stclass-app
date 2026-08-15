# Staging Infrastructure Setup — 1st Class Express TMS

Status as of commit `eacc558` (2026-08-15).

This document exists because the staging pilot is blocked on **external infrastructure that
requires account-holder authorisation**, not on repository work. The application code, tests,
migrations, and Android build pipeline are green. What is missing is a place to run them.

> **No secret values belong in this file.** It lists variable *names* and the console steps that
> produce them. Values go into each provider's secret store.

---

## 1. What is already available on this workstation

Verified by CLI inspection, not assumed:

| Capability | CLI installed | Authenticated | Usable for staging |
|---|---|---|---|
| Vercel | yes | yes (`abubakerasif202`) | **Yes** — dispatcher static hosting |
| GitHub | yes | yes (`abubakerasif202`) | **Yes** — source + Actions |
| Google Cloud | yes | yes | **No** — see below |
| Render | yes | **no** | Blocked on API key |
| Supabase | yes | **no** | Blocked on access token |
| Fly.io / Railway / AWS / Cloudflare | not installed | — | Not available |
| Docker | not installed | — | Local container builds unavailable |

**Google Cloud is authenticated but unusable for hosting.** All five billing accounts on the
signed-in Google account are **closed** (`open: False`). Cloud Run, Cloud SQL, and Cloud Storage
therefore cannot be provisioned. Firebase Cloud Messaging on the free Spark plan does *not*
require billing and remains viable (see §5).

**Vercel cannot host the Transport API.** The backend is a long-lived Express server that holds
open Server-Sent Events streams (`backend/src/sse.ts`, consumed by `dispatcher-web/src/api.ts`
via `EventSource` with credentials). Vercel's serverless functions terminate on a per-invocation
duration cap, which would sever dispatcher realtime updates. Vercel is used for the **dispatcher
only**.

---

## 2. Recommended stack

Chosen to minimise the number of new providers while satisfying the backend's actual runtime
requirements (persistent Node process, SSE, PostgreSQL, private S3-compatible storage).

| Component | Provider | Why |
|---|---|---|
| Transport API | **Render** Web Service (Docker, `backend/Dockerfile`) | Persistent Node process, holds SSE open, free tier available |
| PostgreSQL | **Supabase** Postgres | Free tier is *paused* on inactivity, not deleted (Render's free DB is **deleted after 30 days**) |
| Private object storage | **Supabase** Storage (S3-compatible) | Same project as the DB — avoids a fourth provider |
| Push | **Firebase** Cloud Messaging (Spark/free) | No billing required |
| Dispatcher | **Vercel** | Already authenticated |

> If you would rather keep the database on Render, that works too — but the free Render Postgres
> instance is destroyed 30 days after creation, which will end the pilot abruptly.

---

## 3. Action A — Render (Transport API host)

**Cost note:** Render's free web service tier spins down after ~15 minutes of inactivity. A
cold start takes roughly 50 seconds and open SSE connections are dropped on spin-down. That is
survivable for a pilot but will look like flakiness to a driver. The Starter tier (~USD $7/month)
removes spin-down. **This is a billing decision and has not been taken.**

1. Sign in at <https://dashboard.render.com>.
2. Go to **Account Settings → API Keys → Create API Key**.
3. Provide the key to the agent as `RENDER_API_KEY`, **or** complete steps 4–9 yourself.

Manual path:

4. **New → Web Service**, connect the GitHub repository for this project.
5. **Runtime:** Docker. **Dockerfile path:** `backend/Dockerfile`. **Docker context:** `backend`.
6. **Branch:** `main`. **Region:** Singapore or Oregon (nearest available to AU).
7. **Health check path:** `/health/live`
8. Add the environment variables listed in §6 (names only given there).
9. **Create Web Service**, then record the assigned `https://<name>.onrender.com` URL.

---

## 4. Action B — Supabase (PostgreSQL + private object storage)

1. Sign in at <https://supabase.com/dashboard>.
2. Generate a Personal Access Token: **Account → Access Tokens → Generate new token**.
3. Provide it to the agent as `SUPABASE_ACCESS_TOKEN`, **or** complete steps 4–10 yourself.

Manual path:

4. **New project** — name it `1stclass-express-staging`. Region: **Southeast Asia (Singapore)**
   or the nearest to AU. Set a strong database password and store it in your password manager.
5. **Project Settings → Database → Connection string → URI.** This is `DATABASE_URL`.
   Use the **session pooler** connection string (port `5432`) — the transactional pooler on
   `6543` does not support the session-level behaviour the migrations rely on.
6. **Storage → New bucket** — name `evidence`. **Leave "Public bucket" OFF.** Evidence must stay
   private; the API issues authorised reads.
7. **Project Settings → Storage → S3 Connection.** Note the endpoint and region. These are
   `OBJECT_STORAGE_ENDPOINT` and `OBJECT_STORAGE_REGION`.
8. On the same page, **New access key**. This yields `OBJECT_STORAGE_ACCESS_KEY_ID` and
   `OBJECT_STORAGE_SECRET_ACCESS_KEY`. The secret is shown once.
9. `OBJECT_STORAGE_BUCKET=evidence`, `OBJECT_STORAGE_PREFIX=evidence/`.
10. Migrations are applied from this repository against `DATABASE_URL`:

    ```powershell
    cd backend
    $env:DATABASE_URL = "<staging connection string>"
    npm run db:migrate
    ```

    Two migrations should apply: `001_initial.sql` and `002_entity_operations.sql`.

---

## 5. Action C — Firebase (push notifications)

No Firebase project currently exists for 1st Class Express — this was verified against every
project on the signed-in Google account. The Spark (free) plan is sufficient for FCM and does
**not** require an open billing account.

1. Sign in at <https://console.firebase.google.com>.
2. **Add project** → name `1st-class-express-staging`. Disable Google Analytics (not needed).
3. **Add app → Android.**
   - **Package name:** `au.com.firstclassexpress.driver` — this must match exactly.
   - **Debug signing certificate SHA-1** (pilot key, already generated on this workstation):
     `4F:A2:C2:F1:EB:18:23:05:26:AF:A5:CD:74:54:B3:E6:03:CC:44:D9`
4. Download `google-services.json` and place it at `app/google-services.json`.
   **Do not commit it** — the repository build tolerates its absence
   (`googleServices.missing.passthrough=true`) and it is environment-specific.
5. **Project Settings → Service accounts → Generate new private key.** This downloads a JSON
   file containing the three backend values in §6: `FCM_PROJECT_ID` (`project_id`),
   `FCM_CLIENT_EMAIL` (`client_email`), `FCM_PRIVATE_KEY` (`private_key`).
   Store the file outside the repository. Paste the values into Render's secret store only.

> When pasting `FCM_PRIVATE_KEY` into Render, keep the literal `\n` escape sequences intact.

---

## 6. Environment variable names for the Render service

Names only — set the values from the steps above.

```
NODE_ENV=staging
PORT=8080
JWT_SECRET                       # 32+ random bytes, generate fresh for staging
CORS_ORIGIN                      # exact dispatcher origin, e.g. https://<name>.vercel.app
LOG_LEVEL=info
DATABASE_URL                     # from §4.5
DB_POOL_SIZE=10
IDEMPOTENCY_TTL_MS=86400000
OBJECT_STORAGE_BUCKET            # evidence
OBJECT_STORAGE_REGION            # from §4.7
OBJECT_STORAGE_ENDPOINT          # from §4.7
OBJECT_STORAGE_PREFIX            # evidence/
OBJECT_STORAGE_ACCESS_KEY_ID     # from §4.8
OBJECT_STORAGE_SECRET_ACCESS_KEY # from §4.8
FCM_PROJECT_ID                   # from §5.5
FCM_CLIENT_EMAIL                 # from §5.5
FCM_PRIVATE_KEY                  # from §5.5
```

`CORS_ORIGIN` must be the single exact dispatcher origin. A wildcard would break the credentialed
SSE stream and weaken the session-cookie model.

---

## 7. Action D — Dispatcher (Vercel)

This can be run by the agent once `STAGING_API_URL` exists, because Vercel is already
authenticated. It is deliberately **not** deployed yet: a dispatcher build hard-codes its API
origin at build time (`VITE_API_BASE_URL`), so deploying before the API URL is known would
produce a build that points nowhere.

```powershell
cd dispatcher-web
vercel link --yes
vercel env add VITE_API_BASE_URL preview    # value: the Render https URL, no trailing slash
vercel deploy
```

Then set `CORS_ORIGIN` on the Render service to the resulting Vercel origin and redeploy the API.

---

## 8. Android pilot signing identity — already done

No official production signing key existed. A **dedicated pilot/staging key** has been created
outside the repository, per the pilot plan:

```
Location : C:\Secure\1st-class-express\1stclass-express-PILOT-STAGING.jks
Alias    : pilot-staging
Algorithm: 4096-bit RSA, SHA384withRSA, valid 10 years
SHA-256  : EB:32:AC:D6:F4:C9:11:DF:22:08:76:9B:E1:F5:73:49:CF:F6:F6:AE:6F:4B:62:0E:5D:89:66:F7:CE:1E:80:A4
SHA-1    : 4F:A2:C2:F1:EB:18:23:05:26:AF:A5:CD:74:54:B3:E6:03:CC:44:D9
```

Credentials are stored owner-only at
`C:\Secure\1st-class-express\pilot-keystore-credentials.txt`.

**This is not the production key.** It signs pilot builds only and must never be used for a
Play Store production release.

### Backup requirement

The keystore directory is outside Git and outside any sync folder. **Back it up offline now.**
If it is lost, existing pilot installs cannot be upgraded in place — every pilot device would
need a full uninstall and reinstall.

### Building the signed pilot APK

Once `STAGING_API_URL` is known, this is a single command. Secrets are passed via environment so
they never touch the repository:

```powershell
$env:KEYSTORE_PATH     = "C:\Secure\1st-class-express\1stclass-express-PILOT-STAGING.jks"
$env:KEYSTORE_PASSWORD = "<from credentials file>"
$env:KEY_ALIAS         = "pilot-staging"
$env:KEY_PASSWORD      = "<from credentials file>"
$env:TMS_BASE_URL      = "<STAGING_API_URL>"
.\gradlew.bat :app:assembleRelease
```

The build refuses to proceed if `TMS_BASE_URL` is not `https://`, and refuses to package a
release if any signing value is missing.

### Signing pipeline verified 2026-08-15

The release path was **broken** before this work: Gradle's configuration cache could not serialise
the signing-validation task action, so `assembleRelease`, `packageRelease`, and `bundleRelease` all
failed. CI only built debug, so it was never caught. After the fix, a full signed build was run
against a throwaway `https://pipeline-validation.invalid` endpoint purely to prove the chain:

```
applicationId : au.com.firstclassexpress.driver     (exact — no .staging suffix)
versionName   : 1.0.0
versionCode   : 1
minSdk / target: 24 / 36        (Huawei VOG-L29 is SDK 29 — compatible)
APK           : 17.26 MB, apksigner: verified, APK Signature Scheme v2
AAB           : 16.60 MB, jarsigner: "jar verified"
signer SHA-256: eb32acd6f4c911df2208769be1f57349cff6f6ae6f4b620e5d8966f7ce1e80a4
```

**That artifact is not the pilot build** — it points at a non-existent host and was discarded.
The real pilot APK/AAB must be rebuilt against `STAGING_API_URL` once it exists.

Also verified: a release build with the signing variables unset still fails with
`Release signing is not configured`, so the safety property survived the fix.

---

## 9. Ordering

The dependencies are strictly sequential:

```
Supabase project  ──►  DATABASE_URL + storage keys
                            │
Firebase project  ──►  FCM credentials
                            │
                            ▼
                   Render service  ──►  STAGING_API_URL
                            │
                            ▼
                   Vercel dispatcher ──►  STAGING_DISPATCHER_URL
                            │
                            ▼
                   CORS_ORIGIN pinned, API redeployed
                            │
                            ▼
                   Signed pilot APK built against STAGING_API_URL
                            │
                            ▼
                   Physical Huawei VOG-L29 pilot
```

Nothing downstream of "Render service" can be tested until the one above it exists.

---

## 10. Fastest unblock

Providing these two tokens lets the remainder be automated from this workstation:

- `RENDER_API_KEY` — Render dashboard → Account Settings → API Keys
- `SUPABASE_ACCESS_TOKEN` — Supabase dashboard → Account → Access Tokens

Firebase project creation and the service-account key still require console interaction, because
service-account private keys cannot be issued non-interactively without an authorised gcloud
project.
