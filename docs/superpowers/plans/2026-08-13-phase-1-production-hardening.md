# Phase 1 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the current AI Studio prototype into a locally durable, safety-correct Android driver-app foundation while preserving the existing 1st Class Express Compose experience.

**Architecture:** Keep the current screens and navigation recognizable, but move operational truth out of Compose `remember` state and the monolithic `AppViewModel`. Introduce explicit domain rules, focused repositories, Room persistence, a durable sync queue, and feature ViewModels; remote API, real CameraX capture, GPS tracking and dispatch integration remain outside Phase 1.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose / Material 3, Android Gradle Plugin 9.1.1, Room 2.7.0 with KSP, Coroutines/Flow, AndroidX ViewModel, AndroidX WorkManager for the sync worker shell, JUnit 4, Robolectric, AndroidX Test.

## Global Constraints

- Work directly on `main` as approved, with small logically separated commits.
- Preserve the existing black/gold/red visual identity and current Compose navigation structure where practical.
- Do not change the permanent application ID during Phase 1; keep `com.aistudio.firstclassexpress.abcde` until the deliberate pre-production package migration.
- Do not add production backend authentication, real CameraX capture, Google Maps UI, active GPS tracking, FCM messaging, or dispatcher TMS integration in Phase 1.
- The local Room database is the source of truth for shift, inspection, job, evidence and sync state.
- No safety or evidence operation may report success before durable local persistence succeeds.
- Inspection items default to `UNANSWERED`, never `PASS`.
- Critical defects block shift activation.
- `UNASSIGNED` jobs cannot be started.
- Invalid job-state skips must be rejected by domain logic, not merely hidden in the UI.
- Opening a camera or signature screen must never satisfy an evidence requirement by itself.
- Production code must not use destructive Room migration fallback.
- Keep mock data only as a clearly isolated prototype seed source.

---

## File Structure Locked for Phase 1

### Existing files to modify

- `app/build.gradle.kts` — build cleanup, WorkManager dependency, remove unused Firebase AI/App Check and custom debug signing.
- `build.gradle.kts` — remove plugins that become unused after Firebase/Secrets cleanup if no other feature needs them.
- `gradle/libs.versions.toml` — add WorkManager alias and remove AI-only aliases only when no longer referenced.
- `.env.example` — remove unused Gemini/Firebase AI keys and keep only keys still consumed by the Android app.
- `app/src/main/AndroidManifest.xml` — disable uncontrolled backup for operational data and keep exported components minimal.
- `app/src/main/java/com/example/model/Models.kt` — preserve current screen-facing models while adding only compatibility fields required by the new repositories.
- `app/src/main/java/com/example/data/MockData.kt` — convert into deterministic seed input only.
- `app/src/main/java/com/example/viewmodel/AppViewModel.kt` — reduce to session/login shell plus aggregate read-only app state; remove safety/job mutation authority.
- `app/src/main/java/com/example/navigation/AppNavigation.kt` — wire feature ViewModels and corrected shift/evidence result flow.
- `app/src/main/java/com/example/ui/screens/ShiftStartScreen.kt` — save a shift draft, never activate a shift.
- `app/src/main/java/com/example/ui/screens/PreStartInspectionScreen.kt` — render persisted inspection answers and defect details.
- `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt` — obtain allowed actions from the job domain rule layer.
- `app/src/main/java/com/example/ui/screens/Workflows.kt` — base completion on persisted evidence records.
- `app/src/main/java/com/example/ui/screens/EvidenceScreens.kt` — return an explicit saved/cancelled result; do not report success on navigation.
- Generated/template tests under `app/src/test/java/com/example/` and `app/src/androidTest/java/com/example/` — replace irrelevant template assertions.

### New domain files

- `app/src/main/java/com/example/domain/model/OperationalModels.kt`
- `app/src/main/java/com/example/domain/rules/JobTransitionRules.kt`
- `app/src/main/java/com/example/domain/rules/InspectionRules.kt`
- `app/src/main/java/com/example/domain/rules/ShiftRules.kt`
- `app/src/main/java/com/example/domain/rules/EvidenceRules.kt`
- `app/src/main/java/com/example/domain/repository/ShiftRepository.kt`
- `app/src/main/java/com/example/domain/repository/InspectionRepository.kt`
- `app/src/main/java/com/example/domain/repository/JobRepository.kt`
- `app/src/main/java/com/example/domain/repository/EvidenceRepository.kt`
- `app/src/main/java/com/example/domain/repository/SyncRepository.kt`

### New local data files

- `app/src/main/java/com/example/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/data/local/entity/DriverEntity.kt`
- `app/src/main/java/com/example/data/local/entity/VehicleEntity.kt`
- `app/src/main/java/com/example/data/local/entity/ShiftEntity.kt`
- `app/src/main/java/com/example/data/local/entity/InspectionEntity.kt`
- `app/src/main/java/com/example/data/local/entity/InspectionItemEntity.kt`
- `app/src/main/java/com/example/data/local/entity/JobEntity.kt`
- `app/src/main/java/com/example/data/local/entity/EvidenceEntity.kt`
- `app/src/main/java/com/example/data/local/entity/SyncOperationEntity.kt`
- `app/src/main/java/com/example/data/local/dao/ShiftDao.kt`
- `app/src/main/java/com/example/data/local/dao/InspectionDao.kt`
- `app/src/main/java/com/example/data/local/dao/JobDao.kt`
- `app/src/main/java/com/example/data/local/dao/EvidenceDao.kt`
- `app/src/main/java/com/example/data/local/dao/SyncOperationDao.kt`

### New repository/bootstrapping files

- `app/src/main/java/com/example/data/repository/RoomShiftRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomInspectionRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomJobRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomEvidenceRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomSyncRepository.kt`
- `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- `app/src/main/java/com/example/AppContainer.kt`

### New feature ViewModels

- `app/src/main/java/com/example/viewmodel/ShiftViewModel.kt`
- `app/src/main/java/com/example/viewmodel/InspectionViewModel.kt`
- `app/src/main/java/com/example/viewmodel/JobViewModel.kt`
- `app/src/main/java/com/example/viewmodel/EvidenceViewModel.kt`

### New sync file

- `app/src/main/java/com/example/sync/SyncWorker.kt`

### New/rewritten tests

- `app/src/test/java/com/example/domain/rules/JobTransitionRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/InspectionRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/ShiftRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/EvidenceRulesTest.kt`
- `app/src/test/java/com/example/data/local/AppDatabaseTest.kt`
- `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`
- `app/src/test/java/com/example/viewmodel/ShiftViewModelTest.kt`
- `app/src/test/java/com/example/viewmodel/JobViewModelTest.kt`
- `app/src/test/java/com/example/ui/DriverFlowSmokeTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` — keep only a correct package/application smoke assertion or replace with an app-launch assertion.

---

### Task 1: Stabilize Build Configuration and Remove Generated Template Debt

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `.env.example`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete or rewrite: `app/src/test/java/com/example/GreetingScreenshotTest.kt`
- Delete or rewrite: generated tests that assert `2 + 2`, `My Application`, or unrelated package names
- Modify: `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

**Interfaces:**
- Consumes: current Gradle catalog and Android module configuration.
- Produces: a build that uses standard Android debug signing, contains the Phase 1 dependencies, and has no test references to nonexistent `Greeting()` or unrelated generated application names.

- [ ] **Step 1: Write/replace a meaningful baseline unit test**

Create `app/src/test/java/com/example/AppIdentityTest.kt`:

```kotlin
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun appLabelIsFirstClassExpress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("1st Class Express", context.getString(R.string.app_name))
    }
}
```

- [ ] **Step 2: Run the current unit-test compilation and capture the baseline failures**

Run:

```bash
gradle :app:testDebugUnitTest
```

Expected before cleanup: generated/template tests may fail compilation or assertions because the repository references nonexistent `Greeting()` and stale app/package values.

- [ ] **Step 3: Remove custom debug signing and unused AI dependencies**

In `app/build.gradle.kts`:

- delete the `debugConfig` signing config;
- remove `debug { signingConfig = ... }` so Android uses the standard generated debug keystore;
- remove `implementation(platform(libs.firebase.bom))`, `implementation(libs.firebase.ai)` and `implementation(libs.firebase.appcheck.recaptcha)` because no Phase 1 source consumes them;
- remove `alias(libs.plugins.google.services)` from this module if no Firebase service remains;
- remove the secrets plugin only if `.env` is no longer consumed by a remaining Maps configuration in this phase;
- add WorkManager:

```kotlin
implementation(libs.androidx.work.runtime.ktx)
```

In `gradle/libs.versions.toml` add:

```toml
workRuntimeKtx = "2.10.1"

androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntimeKtx" }
```

If removal of Firebase plugins leaves aliases unused, remove those plugin/library aliases in the same commit.

- [ ] **Step 4: Harden backup behavior**

Update `app/src/main/AndroidManifest.xml` so the application uses:

```xml
android:allowBackup="false"
```

Remove `android:dataExtractionRules` and `android:fullBackupContent` while backup is disabled, so future operational data is not silently included in cloud/device backup.

- [ ] **Step 5: Replace stale generated tests**

`app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` should assert the actual application ID:

```kotlin
package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun applicationIdIsExpectedPrototypeId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aistudio.firstclassexpress.abcde", context.packageName)
    }
}
```

Delete `GreetingScreenshotTest.kt` unless it is rewritten against a real current screen. Delete the meaningless arithmetic test instead of carrying template debt.

- [ ] **Step 6: Run tests again**

Run:

```bash
gradle :app:testDebugUnitTest
```

Expected: unit-test sources compile; `AppIdentityTest` passes; no test references `Greeting`, `My Application`, or `com.example` as the APK application ID.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml .env.example app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "build: stabilize Android project baseline"
```

---

### Task 2: Add Domain Models and Safety-Critical Rule Engines

**Files:**
- Create: `app/src/main/java/com/example/domain/model/OperationalModels.kt`
- Create: `app/src/main/java/com/example/domain/rules/JobTransitionRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/InspectionRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/ShiftRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/EvidenceRules.kt`
- Test: `app/src/test/java/com/example/domain/rules/*.kt`

**Interfaces:**
- Consumes: `com.example.model.JobStatus`.
- Produces:
  - `InspectionItemStatus`
  - `DefectSeverity`
  - `EvidenceStatus`
  - `EvidenceType`
  - `SyncStatus`
  - `ShiftPhase`
  - `ValidationResult`
  - `JobTransitionRules.canTransition(from, to)`
  - `InspectionRules.validate(items, declarationAccepted)`
  - `ShiftRules.canActivate(inspectionValidation)`
  - `EvidenceRules.isSatisfied(status)`

- [ ] **Step 1: Write failing job-transition tests**

Create `JobTransitionRulesTest.kt` with:

```kotlin
@Test
fun unassignedJobCannotStart() {
    assertFalse(JobTransitionRules.canTransition(JobStatus.UNASSIGNED, JobStatus.IN_PROGRESS))
}

@Test
fun assignedJobCanStart() {
    assertTrue(JobTransitionRules.canTransition(JobStatus.ASSIGNED, JobStatus.IN_PROGRESS))
}

@Test
fun jobCannotSkipPickupStages() {
    assertFalse(JobTransitionRules.canTransition(JobStatus.IN_PROGRESS, JobStatus.AT_DELIVERY))
}

@Test
fun validDeliveryCompletionIsAllowed() {
    assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.COMPLETED))
}
```

- [ ] **Step 2: Write failing inspection, shift and evidence tests**

`InspectionRulesTest.kt` must cover:

```kotlin
@Test
fun unansweredMandatoryItemBlocksCompletion() { /* construct UNANSWERED item; expect Invalid */ }

@Test
fun criticalDefectBlocksVehicleReadiness() { /* critical DEFECT with description; expect Blocked */ }

@Test
fun defectRequiresDescriptionAndSeverity() { /* blank description; expect Invalid */ }
```

`EvidenceRulesTest.kt`:

```kotlin
@Test
fun openingCaptureDoesNotSatisfyEvidence() {
    assertFalse(EvidenceRules.isSatisfied(EvidenceStatus.PENDING_CAPTURE))
}

@Test
fun locallySavedEvidenceSatisfiesWorkflowRequirement() {
    assertTrue(EvidenceRules.isSatisfied(EvidenceStatus.SAVED_LOCAL))
}
```

`ShiftRulesTest.kt`:

```kotlin
@Test
fun shiftCannotActivateBeforeValidInspection() {
    assertFalse(ShiftRules.canActivate(ValidationResult.Invalid(listOf("Inspection incomplete"))))
}
```

- [ ] **Step 3: Run rule tests and verify failure**

Run:

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.*"
```

Expected: FAIL because the rule/model classes do not exist yet.

- [ ] **Step 4: Implement operational model types**

`OperationalModels.kt` must define the exact Phase 1 types:

```kotlin
enum class InspectionItemStatus { UNANSWERED, PASS, DEFECT, NOT_APPLICABLE }
enum class DefectSeverity { MINOR, MAJOR, CRITICAL }
enum class EvidenceStatus { NONE, PENDING_CAPTURE, SAVED_LOCAL, PENDING_SYNC, SYNCED, FAILED_SYNC }
enum class EvidenceType { PICKUP_PHOTO, DELIVERY_PHOTO, PICKUP_SIGNATURE, DELIVERY_SIGNATURE, DEFECT_PHOTO, DOCUMENT }
enum class SyncStatus { PENDING, IN_PROGRESS, SYNCED, FAILED }
enum class ShiftPhase { OFF_DUTY, PRESTART_REQUIRED, READY_TO_START, ON_DUTY, ON_BREAK }

data class InspectionAnswer(
    val itemCode: String,
    val mandatory: Boolean,
    val status: InspectionItemStatus,
    val defectDescription: String? = null,
    val defectSeverity: DefectSeverity? = null
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasons: List<String>) : ValidationResult
    data class Blocked(val reasons: List<String>) : ValidationResult
}
```

- [ ] **Step 5: Implement exact job transition map**

`JobTransitionRules.kt`:

```kotlin
object JobTransitionRules {
    private val allowed = mapOf(
        JobStatus.ASSIGNED to setOf(JobStatus.IN_PROGRESS),
        JobStatus.IN_PROGRESS to setOf(JobStatus.AT_PICKUP, JobStatus.ISSUE),
        JobStatus.AT_PICKUP to setOf(JobStatus.PICKED_UP, JobStatus.ISSUE),
        JobStatus.PICKED_UP to setOf(JobStatus.EN_ROUTE_DELIVERY, JobStatus.ISSUE),
        JobStatus.EN_ROUTE_DELIVERY to setOf(JobStatus.AT_DELIVERY, JobStatus.ISSUE),
        JobStatus.AT_DELIVERY to setOf(JobStatus.COMPLETED, JobStatus.ISSUE),
        JobStatus.ISSUE to emptySet()
    )

    fun canTransition(from: JobStatus, to: JobStatus): Boolean =
        allowed[from]?.contains(to) == true
}
```

`UNASSIGNED` and `COMPLETED` intentionally have no outgoing transitions.

- [ ] **Step 6: Implement inspection/shift/evidence rules**

`InspectionRules.validate` must:

1. reject any mandatory `UNANSWERED` item;
2. reject any `DEFECT` without a nonblank description and severity;
3. return `Blocked` when any valid defect has `CRITICAL` severity;
4. require declaration acceptance;
5. return `Valid` only after all checks succeed.

`ShiftRules.canActivate` returns `true` only for `ValidationResult.Valid`.

`EvidenceRules.isSatisfied` returns `true` only for `SAVED_LOCAL`, `PENDING_SYNC`, or `SYNCED`.

- [ ] **Step 7: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.*"
git add app/src/main/java/com/example/domain app/src/test/java/com/example/domain
git commit -m "feat: add driver workflow domain rules"
```

Expected: all domain-rule tests pass.

---

### Task 3: Introduce Room as the Durable Operational Source of Truth

**Files:**
- Create: `app/src/main/java/com/example/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/example/data/local/entity/*.kt`
- Create: `app/src/main/java/com/example/data/local/dao/*.kt`
- Create: `app/src/test/java/com/example/data/local/AppDatabaseTest.kt`

**Interfaces:**
- Consumes: operational enums from Task 2 and existing `JobStatus`.
- Produces:
  - `AppDatabase`
  - `ShiftDao.observeCurrent(): Flow<ShiftEntity?>`
  - `InspectionDao.observeForShift(shiftId: String): Flow<List<InspectionItemEntity>>`
  - `JobDao.observeAll(): Flow<List<JobEntity>>`
  - `EvidenceDao.observeForJob(jobId: String): Flow<List<EvidenceEntity>>`
  - `SyncOperationDao.observePending(): Flow<List<SyncOperationEntity>>`

- [ ] **Step 1: Write failing Room persistence tests**

Use Robolectric with an in-memory Room database. Tests must prove:

```kotlin
@Test
fun shiftDraftSurvivesRepositoryReRead() = runTest { /* insert then query same values */ }

@Test
fun inspectionAnswersArePersistedAsUnansweredUntilDriverResponds() = runTest { /* insert UNANSWERED; query UNANSWERED */ }

@Test
fun jobStatusPersistsAfterUpdate() = runTest { /* insert ASSIGNED; update IN_PROGRESS; query IN_PROGRESS */ }

@Test
fun evidenceRecordPersistsSavedLocalState() = runTest { /* insert SAVED_LOCAL; query same */ }

@Test
fun syncOperationRetainsOriginalCreatedAt() = runTest { /* insert fixed timestamp; query exact timestamp */ }
```

- [ ] **Step 2: Run database tests to verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.local.AppDatabaseTest"
```

Expected: FAIL because Room entities/database do not exist.

- [ ] **Step 3: Define entities**

Use string IDs and enum names stored as strings to keep schema inspection straightforward. Required fields:

```text
DriverEntity: id, name, email
VehicleEntity: id, registration, trailerRegistration
ShiftEntity: id, driverId, vehicleId, trailerId, startOdometer, endOdometer, phase, createdAt, startedAt, endedAt
InspectionEntity: id, shiftId, declarationAccepted, validationState, completedAt
InspectionItemEntity: id, inspectionId, code, label, category, mandatory, status, defectDescription, defectSeverity
JobEntity: id, payloadJson, status, updatedAt
EvidenceEntity: id, jobId, type, localUri, status, createdAt
SyncOperationEntity: id, entityType, entityId, operationType, payloadJson, createdAt, retryCount, lastError, status
```

`payloadJson` is acceptable for the existing rich mock job object in Phase 1 so this phase does not explode into a large relational freight schema.

- [ ] **Step 4: Implement focused DAOs**

Each DAO gets only operations for its aggregate. Example `JobDao`:

```kotlin
@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("UPDATE jobs SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long): Int
}
```

- [ ] **Step 5: Implement database version 1**

`AppDatabase.kt` must register all eight entities and expose the five DAOs. Do not call `fallbackToDestructiveMigration()` in production construction.

- [ ] **Step 6: Run Room tests**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.local.AppDatabaseTest"
```

Expected: all persistence tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/data/local app/src/test/java/com/example/data/local
git commit -m "feat: add durable Room operational database"
```

---

### Task 4: Add Repository Contracts, Room Implementations, Prototype Seeding and Sync Queue

**Files:**
- Create: `app/src/main/java/com/example/domain/repository/*.kt`
- Create: `app/src/main/java/com/example/data/repository/*.kt`
- Create: `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- Modify: `app/src/main/java/com/example/data/MockData.kt`
- Create: `app/src/main/java/com/example/AppContainer.kt`
- Create: `app/src/main/java/com/example/sync/SyncWorker.kt`
- Test: `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`

**Interfaces:**
- Produces exact repository contracts:

```kotlin
interface ShiftRepository {
    fun observeCurrentShift(): Flow<ShiftEntity?>
    suspend fun createPreStartDraft(vehicleId: String, trailerId: String?, startOdometer: Long): Result<String>
    suspend fun activateShift(shiftId: String): Result<Unit>
    suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit>
}

interface InspectionRepository {
    fun observeItems(shiftId: String): Flow<List<InspectionItemEntity>>
    suspend fun createForShift(shiftId: String): Result<String>
    suspend fun saveAnswer(itemId: String, answer: InspectionAnswer): Result<Unit>
    suspend fun setDeclaration(inspectionId: String, accepted: Boolean): Result<Unit>
    suspend fun validate(inspectionId: String): ValidationResult
}

interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    suspend fun getJob(id: String): Job?
    suspend fun transition(id: String, to: JobStatus): Result<JobStatus>
}

interface EvidenceRepository {
    fun observeForJob(jobId: String): Flow<List<EvidenceEntity>>
    suspend fun createPending(jobId: String, type: EvidenceType): Result<String>
    suspend fun markSavedLocal(id: String, localUri: String): Result<Unit>
    suspend fun discardPending(id: String): Result<Unit>
}

interface SyncRepository {
    fun observePending(): Flow<List<SyncOperationEntity>>
    suspend fun enqueue(entityType: String, entityId: String, operationType: String, payloadJson: String): Result<String>
    suspend fun markFailure(id: String, error: String): Result<Unit>
    suspend fun markSynced(id: String): Result<Unit>
}
```

- [ ] **Step 1: Write failing repository tests**

Required behaviors:

- `createPreStartDraft` persists `PRESTART_REQUIRED`, not `ON_DUTY`;
- `activateShift` refuses activation when inspection validation is not valid;
- `JobRepository.transition` refuses `UNASSIGNED -> IN_PROGRESS` and `ASSIGNED -> AT_DELIVERY`;
- valid job transitions update Room and enqueue exactly one sync operation;
- evidence is not considered saved until `markSavedLocal` succeeds;
- sync queue preserves the initial operation timestamp across retries.

- [ ] **Step 2: Run tests and verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
```

- [ ] **Step 3: Implement repository interfaces and Room implementations**

Use `AppDatabase.withTransaction` for state change + sync queue insertion so the UI never sees a local status mutation without its matching pending sync operation.

For example, `RoomJobRepository.transition` must perform:

```kotlin
val current = jobDao.getById(id) ?: return Result.failure(IllegalArgumentException("Job not found"))
val from = JobStatus.valueOf(current.status)
if (!JobTransitionRules.canTransition(from, to)) {
    return Result.failure(IllegalStateException("Invalid job transition: $from -> $to"))
}

database.withTransaction {
    jobDao.updateStatus(id, to.name, clock())
    syncDao.insert(
        SyncOperationEntity(
            id = uuid(),
            entityType = "JOB",
            entityId = id,
            operationType = "STATUS_CHANGE",
            payloadJson = "{\"status\":\"${to.name}\"}",
            createdAt = clock(),
            retryCount = 0,
            lastError = null,
            status = SyncStatus.PENDING.name
        )
    )
}
```

- [ ] **Step 4: Move mock data into seed-only use**

`PrototypeSeedData.kt` converts `MockData.sampleJobs` and `MockData.currentDriver` into Room rows only when the corresponding tables are empty. Screens/ViewModels must no longer import `MockData` after their migrations in later tasks.

- [ ] **Step 5: Add `AppContainer`**

Create a single application-level composition root that builds `AppDatabase` and repository instances. Keep constructor injection manual in Phase 1; do not introduce Hilt solely for this migration.

- [ ] **Step 6: Add a non-destructive sync worker shell**

`SyncWorker` reads pending operations and returns:

```kotlin
Result.success()
```

without marking records synced when no remote transport is configured. Its job in Phase 1 is only to prove durable WorkManager integration and preserve queue contents. It must never delete or falsely acknowledge unsent operations.

- [ ] **Step 7: Run repository tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
git add app/src/main/java/com/example/domain/repository app/src/main/java/com/example/data/repository app/src/main/java/com/example/data/seed app/src/main/java/com/example/AppContainer.kt app/src/main/java/com/example/sync app/src/test/java/com/example/data/repository
git commit -m "feat: add local-first repositories and sync queue"
```

---

### Task 5: Correct Shift and Pre-Start Workflow End-to-End

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/ShiftViewModel.kt`
- Create: `app/src/main/java/com/example/viewmodel/InspectionViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ShiftStartScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/PreStartInspectionScreen.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Test: `app/src/test/java/com/example/viewmodel/ShiftViewModelTest.kt`

**Interfaces:**
- Consumes: `ShiftRepository`, `InspectionRepository`.
- Produces:
  - `ShiftViewModel.beginPreStart(vehicleId, trailerId, odometer)`
  - `InspectionViewModel.setAnswer(itemId, status, description, severity)`
  - `InspectionViewModel.setDeclaration(accepted)`
  - `InspectionViewModel.completeAndActivateShift()`

- [ ] **Step 1: Write failing ViewModel workflow tests**

Required assertions:

```kotlin
@Test
fun beginPreStartDoesNotSetOnDuty() = runTest { /* draft phase == PRESTART_REQUIRED */ }

@Test
fun leavingIncompleteInspectionDoesNotActivateShift() = runTest { /* phase remains PRESTART_REQUIRED */ }

@Test
fun allMandatoryItemsMustBeAnswered() = runTest { /* completion rejected */ }

@Test
fun criticalDefectKeepsShiftBlocked() = runTest { /* phase never ON_DUTY */ }

@Test
fun validInspectionActivatesShiftOnlyAfterPersistence() = runTest { /* phase becomes ON_DUTY */ }
```

- [ ] **Step 2: Run tests and verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.ShiftViewModelTest"
```

- [ ] **Step 3: Implement `ShiftViewModel`**

`beginPreStart` validates numeric odometer, creates a Room draft and creates the inspection. It emits a navigation event only after both durable operations succeed. It never calls an `ON_DUTY` mutation.

- [ ] **Step 4: Implement `InspectionViewModel`**

Expose a `StateFlow<InspectionUiState>` containing persisted item answers, declaration state, validation messages and `canComplete`. `completeAndActivateShift()` must:

1. validate through `InspectionRepository`;
2. if `Valid`, call `ShiftRepository.activateShift`;
3. emit completion navigation only after activation succeeds;
4. preserve current state and show reasons for `Invalid` or `Blocked`.

- [ ] **Step 5: Rewrite `ShiftStartScreen` interaction**

Replace:

```kotlin
viewModel.startShift(vehicleId, odometer)
onNavigateToInspection()
```

with a single ViewModel call that saves the draft. Navigate only after the ViewModel emits `PreStartCreated(shiftId)`.

Do not keep vehicle ID or odometer as business truth in `remember` after submission.

- [ ] **Step 6: Rewrite inspection item state**

Every rendered item comes from persisted `InspectionItemEntity.status`. Initial selection is none (`UNANSWERED`). Render three actions:

```text
PASS | DEFECT | N/A
```

When `DEFECT` is selected, show required severity (`MINOR`, `MAJOR`, `CRITICAL`) and description. The declaration checkbox is insufficient by itself; `Complete Inspection` is enabled only when the repository/domain validation says the form is complete and has no blocking critical defect.

- [ ] **Step 7: Wire navigation and remove `AppViewModel.startShift` authority**

`AppNavigation.kt` obtains feature ViewModels from repositories in `AppContainer`. `AppViewModel.startShift` is deleted after all callers move to `ShiftViewModel`.

- [ ] **Step 8: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.ShiftViewModelTest"
git add app/src/main/java/com/example/viewmodel app/src/main/java/com/example/ui/screens/ShiftStartScreen.kt app/src/main/java/com/example/ui/screens/PreStartInspectionScreen.kt app/src/main/java/com/example/navigation/AppNavigation.kt app/src/test/java/com/example/viewmodel
git commit -m "fix: enforce pre-start before shift activation"
```

---

### Task 6: Move Job Progression Authority into the Repository and Domain Layer

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/JobViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/JobsListScreen.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Test: `app/src/test/java/com/example/viewmodel/JobViewModelTest.kt`

**Interfaces:**
- Consumes: `JobRepository`, `JobTransitionRules`.
- Produces:
  - `JobViewModel.observeJob(jobId)`
  - `JobViewModel.requestTransition(to: JobStatus)`
  - `JobUiState.allowedNextStatuses`
  - `JobUiState.errorMessage`

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun unassignedJobHasNoStartAction() = runTest { /* allowedNextStatuses excludes IN_PROGRESS */ }

@Test
fun assignedJobOffersInProgressOnly() = runTest { /* includes IN_PROGRESS; excludes AT_DELIVERY */ }

@Test
fun invalidTransitionReturnsVisibleErrorAndDoesNotMutateRoom() = runTest { /* status unchanged */ }
```

- [ ] **Step 2: Run tests to verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.JobViewModelTest"
```

- [ ] **Step 3: Implement `JobViewModel` from repository state**

Allowed actions are calculated from the current persisted status and `JobTransitionRules`. UI requests go through `JobRepository.transition`; the ViewModel never writes a copied in-memory job list directly.

- [ ] **Step 4: Fix `JobDetailScreen`**

Remove the current branch that treats `UNASSIGNED` and `ASSIGNED` identically. The screen renders actions from `allowedNextStatuses` and shows a validation message if the repository rejects a transition.

- [ ] **Step 5: Remove `AppViewModel.updateJobStatus` authority**

After all job-screen callers use `JobViewModel`, delete the unrestricted `updateJobStatus(jobId, newStatus)` function and make `AppViewModel` observe jobs from `JobRepository` only if aggregate home-screen state still needs them.

- [ ] **Step 6: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.JobViewModelTest"
git add app/src/main/java/com/example/viewmodel/JobViewModel.kt app/src/main/java/com/example/ui/screens/JobDetailScreen.kt app/src/main/java/com/example/ui/screens/JobsListScreen.kt app/src/main/java/com/example/navigation/AppNavigation.kt app/src/main/java/com/example/viewmodel/AppViewModel.kt app/src/test/java/com/example/viewmodel/JobViewModelTest.kt
git commit -m "fix: validate driver job state transitions"
```

---

### Task 7: Make Photo and Signature Completion Depend on Persisted Evidence

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/EvidenceViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/Workflows.kt`
- Modify: `app/src/main/java/com/example/ui/screens/EvidenceScreens.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Test: `app/src/test/java/com/example/domain/rules/EvidenceRulesTest.kt`
- Test: `app/src/test/java/com/example/ui/DriverFlowSmokeTest.kt`

**Interfaces:**
- Consumes: `EvidenceRepository`, `EvidenceRules`.
- Produces:
  - `EvidenceViewModel.beginCapture(jobId, type)` -> pending evidence ID
  - `EvidenceViewModel.confirmSaved(evidenceId, localUri)`
  - `EvidenceViewModel.cancelCapture(evidenceId)`
  - `EvidenceViewModel.hasSatisfiedEvidence(jobId, type)`

- [ ] **Step 1: Add a failing smoke test for cancelled capture**

The test must drive the pickup workflow as follows:

```text
Open pickup -> Tap Take Photo -> capture screen opens -> Back/Cancel -> return to pickup
```

Expected: pickup still reports photo evidence as missing and completion remains disabled when photo evidence is required.

Add the same behavior for signature capture.

- [ ] **Step 2: Run the evidence/smoke tests and verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.EvidenceRulesTest" --tests "com.example.ui.DriverFlowSmokeTest"
```

- [ ] **Step 3: Implement `EvidenceViewModel`**

`beginCapture` persists `PENDING_CAPTURE`. `confirmSaved` requires a nonblank local URI and persists `SAVED_LOCAL` plus a pending sync operation. `cancelCapture` deletes/discards only the pending placeholder and never creates a satisfied state.

- [ ] **Step 4: Fix workflow screens**

Delete patterns equivalent to:

```kotlin
hasPhoto = true
onNavigateToCamera()
```

and:

```kotlin
hasSignature = true
onNavigateToSignature()
```

The workflow derives its evidence checklist from `EvidenceRepository.observeForJob`. Only statuses accepted by `EvidenceRules.isSatisfied` render as complete.

- [ ] **Step 5: Make evidence screens return explicit results**

In Phase 1, the existing prototype capture UI may still use a mock local file/URI generator, but the result contract must be explicit:

```kotlin
sealed interface CaptureResult {
    data class Saved(val localUri: String) : CaptureResult
    data object Cancelled : CaptureResult
}
```

`Saved` is passed to `confirmSaved`; `Cancelled` is passed to `cancelCapture`.

The signature screen must not claim durable POD metadata beyond what Phase 1 actually persists.

- [ ] **Step 6: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.EvidenceRulesTest" --tests "com.example.ui.DriverFlowSmokeTest"
git add app/src/main/java/com/example/viewmodel/EvidenceViewModel.kt app/src/main/java/com/example/ui/screens/Workflows.kt app/src/main/java/com/example/ui/screens/EvidenceScreens.kt app/src/main/java/com/example/navigation/AppNavigation.kt app/src/test/java/com/example/domain/rules/EvidenceRulesTest.kt app/src/test/java/com/example/ui/DriverFlowSmokeTest.kt
git commit -m "fix: require persisted proof before workflow completion"
```

---

### Task 8: Seed Prototype Data Through Room and Finish App-State Migration

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Modify: `app/src/main/java/com/example/data/MockData.kt`
- Modify: `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Test: `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`

**Interfaces:**
- Consumes: `AppContainer`, repository flows and deterministic prototype seed.
- Produces: one app startup path in which mock content is inserted once into Room and every operational screen observes repository-backed state.

- [ ] **Step 1: Add a failing seed-idempotency test**

Run prototype seeding twice against an empty database and assert that jobs, driver and seed vehicles exist once only with stable IDs.

- [ ] **Step 2: Run test and verify failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
```

- [ ] **Step 3: Initialize `AppContainer` once at app startup**

Keep manual dependency construction. `MainActivity` or a small `Application` subclass owns the container; Compose receives repositories/ViewModel factories rather than constructing database objects in composables.

- [ ] **Step 4: Make seeding idempotent**

`PrototypeSeedData.seedIfEmpty()` checks table counts before insertion. It never overwrites driver-entered changes or statuses on later launches.

- [ ] **Step 5: Reduce `AppViewModel` to session/aggregate presentation state**

It may still contain the temporary prototype login until production authentication is built, but it must no longer be the source of truth for shift status, job status, inspection answers or evidence completion.

- [ ] **Step 6: Search for prohibited direct state paths**

Run:

```bash
git grep -n "MockData" -- app/src/main/java
git grep -n "updateJobStatus" -- app/src/main/java
git grep -n "startShift(" -- app/src/main/java
git grep -n "mutableStateOf<String?>(\"PASS\")" -- app/src/main/java
```

Expected:

- `MockData` appears only in the seed adapter or is replaced completely by `PrototypeSeedData`;
- unrestricted `updateJobStatus` is absent;
- the old shift activation method is absent;
- inspection items never default to `PASS`.

- [ ] **Step 7: Run tests and commit**

```bash
gradle :app:testDebugUnitTest
git add app/src/main/java/com/example app/src/test/java/com/example
git commit -m "refactor: make Room the operational source of truth"
```

---

### Task 9: Full Verification and Phase 1 Acceptance Gate

**Files:**
- Modify only files needed to fix verification failures directly caused by Tasks 1-8.
- Update: `README.md` with the actual Phase 1 architecture and run instructions if README still describes Gemini API requirements that no longer exist.

**Interfaces:**
- Consumes: all Phase 1 work.
- Produces: a verified `main` branch meeting the approved design acceptance criteria.

- [ ] **Step 1: Run all unit tests**

```bash
gradle :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Compile debug APK**

```bash
gradle :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; no repository-local `debug.keystore` is required.

- [ ] **Step 3: Compile instrumentation tests**

```bash
gradle :app:assembleDebugAndroidTest
```

Expected: instrumentation test APK compiles against the real application ID and current source tree.

- [ ] **Step 4: Verify safety invariants by targeted tests**

```bash
gradle :app:testDebugUnitTest \
  --tests "com.example.domain.rules.JobTransitionRulesTest" \
  --tests "com.example.domain.rules.InspectionRulesTest" \
  --tests "com.example.domain.rules.ShiftRulesTest" \
  --tests "com.example.domain.rules.EvidenceRulesTest" \
  --tests "com.example.viewmodel.ShiftViewModelTest" \
  --tests "com.example.viewmodel.JobViewModelTest"
```

Expected: PASS.

- [ ] **Step 5: Verify acceptance criteria with source searches**

```bash
git grep -n "debugConfig" -- app/build.gradle.kts || true
git grep -n "firebase.ai\|firebase-ai\|GEMINI_API_KEY" -- . ':!docs' || true
git grep -n "mutableStateOf<String?>(\"PASS\")" -- app/src/main/java || true
git grep -n "JobStatus.UNASSIGNED, JobStatus.ASSIGNED" -- app/src/main/java || true
git grep -n "hasPhoto = true\|hasSignature = true" -- app/src/main/java || true
```

Expected: no matches for the old unsafe/generated patterns.

- [ ] **Step 6: Review current diff and commit only verification/documentation fixes**

```bash
git status
git diff --check
git diff
```

Fix whitespace or verification-only defects, rerun the affected tests, then:

```bash
git add README.md app
git commit -m "docs: document hardened driver app foundation"
```

Skip the commit when verification produces no file changes.

- [ ] **Step 7: Final repository state check**

```bash
git status --short
git log --oneline -10
```

Expected: clean working tree and a sequence of small Phase 1 commits on `main`.

---

## Phase 1 Completion Checklist

- [ ] Debug builds no longer depend on a checked-in `debug.keystore`.
- [ ] Generated template tests no longer reference nonexistent or unrelated app code.
- [ ] The driver cannot become `ON_DUTY` before a valid pre-start inspection.
- [ ] Mandatory inspection items begin `UNANSWERED` and must receive an explicit response.
- [ ] Defects require description and severity; `CRITICAL` blocks shift activation.
- [ ] `UNASSIGNED` jobs cannot start and invalid status skips are rejected by domain/repository logic.
- [ ] Camera/signature navigation cannot falsely satisfy evidence requirements.
- [ ] Shift, inspection, job, evidence and sync data persist in Room.
- [ ] State-changing repository transactions enqueue durable sync operations without falsely marking them remotely synced.
- [ ] Mock transport content is seed-only and does not overwrite later local state.
- [ ] The current UI remains recognizable and navigable.
- [ ] Unit tests and debug/instrumentation compilation succeed.
