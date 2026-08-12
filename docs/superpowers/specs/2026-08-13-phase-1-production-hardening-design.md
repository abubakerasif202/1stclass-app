# 1st Class Express Driver App — Phase 1 Production Hardening Design

Date: 2026-08-13
Repository: `abubakerasif202/1stclass-app`
Target branch: `main`

## Goal

Harden the existing AI Studio Android prototype into a reliable foundation for operational driver use without discarding the current Compose UI. This phase focuses on build stability, safety-critical workflow correctness, durable local data, validated job state transitions, and a clean architecture that later phases can extend with real backend, CameraX, GPS/maps, push notifications, and dispatch integration.

## Scope

Phase 1 will implement the following:

1. Build and project hygiene
2. Test baseline cleanup
3. Shift and pre-start safety workflow correction
4. Job status/domain validation
5. Evidence completion integrity
6. Repository/domain/data layer foundation
7. Room local persistence foundation
8. Offline sync queue foundation
9. Security/configuration cleanup required for the above

Phase 1 will not attempt to fully implement the live backend, production authentication server, CameraX capture, Google Maps UI, location tracking service, FCM push messaging, or full dispatch TMS integration. Those are intentionally left for later phases after the foundation is stable.

## Architecture

The current app places most state and business behavior in one `AppViewModel`. Phase 1 will introduce bounded layers while preserving existing screens as much as practical:

```text
presentation/
  navigation/
  screens/
  viewmodel/

domain/
  model/
  repository/
  rules/

data/
  local/
    entity/
    dao/
    database/
  repository/
  mock/

sync/
  model/
  queue/
```

The UI must depend on ViewModels and domain-facing repository interfaces, not on mock data or Room DAOs directly.

## Data flow

The intended local-first flow is:

```text
Driver action
  -> ViewModel
  -> domain validation
  -> repository
  -> Room transaction
  -> UI observes durable local state
  -> sync queue records pending remote work
```

For this phase, the remote side remains a stub/mock boundary. The local state becomes the source of truth so process death or temporary connectivity loss does not erase operational state.

## Shift and pre-start workflow

The existing flow starts a shift before inspection completion. Phase 1 will change it to:

```text
OFF_DUTY
  -> vehicle selected
  -> start odometer entered
  -> PRESTART_REQUIRED
  -> inspection completed
  -> inspection validated
  -> READY_TO_START
  -> ON_DUTY
```

Rules:

- Shift must not become `ON_DUTY` until pre-start completion succeeds.
- Every inspection item starts as `UNANSWERED`, never `PASS`.
- Submission is blocked while mandatory items remain unanswered.
- Inspection responses support `PASS`, `DEFECT`, and `NOT_APPLICABLE`.
- A defect requires at least a description and severity.
- Critical defects block shift start.
- Non-critical defects may allow continuation while remaining recorded for later dispatch handling.
- Leaving the inspection screen must not silently activate the shift.
- Inspection state must be persisted locally.

## Job state machine

Job status changes will move out of button-specific UI logic into domain rules.

A permitted baseline sequence is:

```text
ASSIGNED
  -> IN_PROGRESS
  -> AT_PICKUP
  -> PICKED_UP
  -> EN_ROUTE_DELIVERY
  -> AT_DELIVERY
  -> COMPLETED
```

Additional rules:

- `UNASSIGNED` jobs cannot be started by a driver.
- Invalid skips are rejected by domain logic.
- Completing pickup requires pickup evidence requirements to be satisfied.
- Completing delivery requires delivery evidence requirements to be satisfied.
- The UI may hide unavailable actions, but domain validation remains authoritative.

## Evidence integrity

Current pickup/delivery flows mark photo or signature state before capture succeeds. Phase 1 will change the model so evidence completion is based on persisted evidence records only.

Evidence states:

```text
NONE
PENDING_CAPTURE
SAVED_LOCAL
PENDING_SYNC
SYNCED
FAILED_SYNC
```

A photo/signature action must not count as complete merely because the driver opened the capture screen. Only a successfully persisted local evidence record can satisfy a workflow requirement.

Phase 1 may use a local/mock evidence persistence implementation if real CameraX output is not yet implemented, but the state contract must match the later production capture flow.

## Local database

Introduce Room with a minimal schema for Phase 1:

- `DriverEntity`
- `VehicleEntity`
- `ShiftEntity`
- `InspectionEntity`
- `InspectionItemEntity`
- `JobEntity`
- `EvidenceEntity`
- `SyncOperationEntity`

DAOs will be focused by responsibility rather than using one large DAO.

The database should be versioned from `1` and configured without destructive migration fallback in production code.

## Repository boundaries

Introduce interfaces such as:

- `ShiftRepository`
- `InspectionRepository`
- `JobRepository`
- `EvidenceRepository`
- `SyncRepository`

Initial implementations may use Room plus current mock seed data. This preserves prototype usability while removing direct coupling between screens and `MockData`.

## Offline sync foundation

Phase 1 will add a durable sync queue model, even if full network upload is deferred.

Every syncable local action records:

- operation ID
- entity type
- entity ID
- operation type
- created timestamp
- retry count
- last error
- sync status

The queue contract must support idempotent later remote submission. WorkManager wiring may be introduced as a basic worker shell if dependency/configuration support is added, but real endpoint calls are out of scope for this phase.

## Build and configuration cleanup

Phase 1 will:

- remove the hard dependency on a repository-local `debug.keystore`
- use the standard Android debug signing behavior
- retain release signing through environment-based configuration only where valid
- remove or isolate unused AI dependencies that are not required by the driver app foundation
- remove unnecessary secrets expectations for unused Gemini functionality
- retain the current package name during Phase 1 unless changing it is required to compile; permanent application ID migration will be handled deliberately before production distribution
- make the project reproducible from a normal Android Studio/Gradle environment

## Manifest and security baseline

Phase 1 will not add permissions for features not yet implemented. It will, however:

- review backup behavior and avoid exposing future operational data through uncontrolled backup
- prepare the manifest for later camera/location/notification permissions without adding unused permission prompts
- keep exported components minimal

## Tests

Replace placeholder/generated tests that assert irrelevant values.

Minimum Phase 1 tests:

### Unit tests

- valid and invalid job status transitions
- unassigned job cannot start
- shift cannot start before completed pre-start
- unanswered inspection items block completion
- critical defect blocks shift activation
- evidence must be persisted before satisfying completion rules

### Repository/local tests

- Room entities persist and restore shift state
- inspection responses survive recreation
- job state survives recreation
- sync operations are queued once and retain timestamps

### UI/navigation smoke tests

- login -> shift start -> pre-start -> home
- back from incomplete pre-start does not start shift
- unavailable job actions are not shown

Tests must assert actual `1st Class Express` behavior rather than generated template values such as `Greeting`, `My Application`, or unrelated package names.

## Error handling

Domain operations return explicit success/failure results rather than silently mutating state.

Examples:

- invalid transition -> validation error displayed to driver
- incomplete inspection -> list or summary of missing items
- critical defect -> clear blocked-state message
- local persistence failure -> operation not marked complete
- sync queue failure -> local action remains preserved and visible as pending

The app must never report success for a safety or evidence action before durable local persistence succeeds.

## Migration strategy from current prototype

The existing UI will be migrated incrementally:

1. Keep visual screens and navigation where practical.
2. Replace direct `AppViewModel` mutations with feature/domain methods.
3. Seed Room from current mock data for prototype mode.
4. Move workflow truth from local Compose `remember` state into ViewModels/repositories for operational data.
5. Remove obsolete state fields only after each screen is wired to the new source of truth.

This avoids a full UI rewrite and reduces regression risk.

## Acceptance criteria

Phase 1 is complete when:

- project configuration no longer depends on a checked-in debug keystore
- broken template tests are removed or replaced
- shift cannot enter `ON_DUTY` before a valid pre-start
- all inspection items default to unanswered
- critical defects block shift start
- unassigned jobs cannot be started
- invalid job status skips are rejected outside the UI layer
- opening camera/signature screens cannot falsely satisfy evidence requirements
- core shift/job/inspection/evidence state is durable in Room
- a persistent sync queue exists for later backend integration
- the existing prototype remains navigable and visually recognizable
- new domain rules have automated tests

## Deferred to Phase 2+

- production auth/token refresh
- real Retrofit backend implementation
- real CameraX image capture/compression
- signature bitmap/file persistence with GPS metadata
- Google Maps UI and route navigation
- active-shift GPS tracking/foreground service
- FCM dispatch messaging
- complete exception/issue reporting
- full WorkManager remote sync execution
- admin/dispatcher TMS backend integration

## Implementation approach

Work directly on `main` as requested, but keep commits small and logically separated so each hardening step can be reviewed and reverted independently if necessary.
