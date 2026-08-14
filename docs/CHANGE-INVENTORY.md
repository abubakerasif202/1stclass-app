# Release remediation change inventory

This inventory records ownership and intent for the dirty worktree reviewed before the release-remediation commit.

## A. Previously committed baseline

- `main` started this run at `9e4e02a` (`feat: add driver sync status ui and diagnostics screen`).
- The tracked baseline contains the Android driver application and its existing production-hardening history.

## B. Existing user and AGY implementation

- The Android package migration replaces tracked `app/src/**/com/example/**` paths with `app/src/**/au/com/firstclassexpress/driver/**` paths.
- `backend/` contains the Transport API, persistence and evidence-storage adapters, migrations, tests, and deployment configuration.
- `dispatcher-web/` contains the dispatcher control-centre application, API client, views, tests, and deployment configuration.
- `docs/PILOT-RUNBOOK.md`, `docs/SECURITY.md`, `docs/TMS-ARCHITECTURE.md`, and `scripts/` are project implementation and operational documentation.

## C. Previous Codex hardening work

- Authentication, authorization, JWT, CORS, rate-limit, idempotency, persistence, storage, upload-validation, FCM, Android identity, and readiness changes already present when this run began are preserved for verification and correction.
- `docs/PRODUCTION-READINESS.md` is the evidence checklist to be reconciled after all gates run.

## D. Changes made in this run

- Repaired Node dependency lock/install state under Windows Node 20.
- Added repository ignore and line-ending policy for generated artifacts and Windows/Linux collaboration.
- Added this inventory; subsequent fixes and verification evidence are recorded in the final diff and readiness document.

## Worktree classification policy

- **PROJECT IMPLEMENTATION - COMMIT:** Android migrated source/tests, backend source/tests/migrations/config, dispatcher source/tests/config, operational docs/scripts, CI, ignore and line-ending policy.
- **GENERATED - IGNORE:** `node_modules/`, backend/dispatcher `dist/`, Gradle/build output, APK/AAB output, and test compilation output.
- **TEMPORARY - PRESERVE BUT DO NOT COMMIT:** `.claude/worktrees/`, local logs, screenshots, and pilot artifacts.
- **SECRET - NEVER COMMIT:** `.env`, service-account files, keystores, signing credentials, database dumps, uploaded evidence, and live tokens.
- **UNRELATED USER WORK - PRESERVE:** none identified at the start of this run; any later discovery remains unstaged until explicitly reviewed.
