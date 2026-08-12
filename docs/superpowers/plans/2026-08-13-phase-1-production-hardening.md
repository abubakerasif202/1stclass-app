# Phase 1 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the current AI Studio prototype into a locally durable, safety-correct Android driver-app foundation while preserving the existing 1st Class Express Compose experience.

**Architecture:** Keep the current screens visually recognizable, but move shift, inspection, job, evidence and sync truth out of Compose `remember` state and the monolithic `AppViewModel`. Domain rules decide what is legal; Room stores durable state; repositories are the only mutation boundary; feature ViewModels translate repository state into Compose UI state. Production backend auth, real CameraX, Google Maps, live GPS and FCM remain outside Phase 1.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose / Material 3, Android Gradle Plugin 9.1.1, Room 2.7.0 with KSP, Moshi, Coroutines/Flow, AndroidX ViewModel, JUnit 4, Robolectric and AndroidX Test.

## Global Constraints

- Work directly on `main` as approved, with small logically separated commits.
- Preserve the existing black/gold/red visual identity and current Compose navigation structure where practical.
- Keep the current application ID `com.aistudio.firstclassexpress.abcde` during Phase 1.
- Do not add production backend authentication, real CameraX capture, Google Maps UI, active GPS tracking, FCM messaging or dispatcher TMS integration in Phase 1.
- Room is the local source of truth for shift, inspection, job, evidence and sync state.
- No safety or evidence operation may report success before durable local persistence succeeds.
- Inspection items default to `UNANSWERED`, never `PASS`.
- Critical defects block shift activation.
- `UNASSIGNED` jobs cannot be started.
- Invalid job-state skips are rejected by domain/repository logic even if a UI bug exposes the wrong button.
- Opening a camera or signature screen never satisfies an evidence requirement.
- Production database construction must not call `fallbackToDestructiveMigration()`.
- Mock transport data is seed-only and cannot overwrite driver-entered local state on later launches.
- Phase 1 does not claim that prototype camera/signature evidence is production POD; the data contract is hardened first and real capture replaces the prototype in Phase 2.

---

## Locked File Structure

### Existing files to modify

- `app/build.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/model/Models.kt`
- `app/src/main/java/com/example/data/MockData.kt` — removed after its data is moved into seed-only code.
- `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- `app/src/main/java/com/example/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/ui/screens/ShiftStartScreen.kt`
- `app/src/main/java/com/example/ui/screens/PreStartInspectionScreen.kt`
- `app/src/main/java/com/example/ui/screens/JobsListScreen.kt`
- `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt`
- `app/src/main/java/com/example/ui/screens/Workflows.kt`
- `app/src/main/java/com/example/ui/screens/EvidenceScreens.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

### Existing generated files to delete

- `.env.example` — it contains only the unused Gemini placeholder.
- `app/src/test/java/com/example/ExampleUnitTest.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `app/src/test/java/com/example/GreetingScreenshotTest.kt`

### New domain files

- `app/src/main/java/com/example/domain/model/OperationalModels.kt`
- `app/src/main/java/com/example/domain/model/InspectionChecklist.kt`
- `app/src/main/java/com/example/domain/rules/JobTransitionRules.kt`
- `app/src/main/java/com/example/domain/rules/InspectionRules.kt`
- `app/src/main/java/com/example/domain/rules/ShiftRules.kt`
- `app/src/main/java/com/example/domain/rules/EvidenceRules.kt`
- `app/src/main/java/com/example/domain/repository/DriverRepository.kt`
- `app/src/main/java/com/example/domain/repository/ShiftRepository.kt`
- `app/src/main/java/com/example/domain/repository/InspectionRepository.kt`
- `app/src/main/java/com/example/domain/repository/JobRepository.kt`
- `app/src/main/java/com/example/domain/repository/EvidenceRepository.kt`
- `app/src/main/java/com/example/domain/repository/SyncRepository.kt`

### New local-data files

- `app/src/main/java/com/example/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/data/local/JobPayloadCodec.kt`
- `app/src/main/java/com/example/data/local/entity/DriverEntity.kt`
- `app/src/main/java/com/example/data/local/entity/VehicleEntity.kt`
- `app/src/main/java/com/example/data/local/entity/ShiftEntity.kt`
- `app/src/main/java/com/example/data/local/entity/InspectionEntity.kt`
- `app/src/main/java/com/example/data/local/entity/InspectionItemEntity.kt`
- `app/src/main/java/com/example/data/local/entity/JobEntity.kt`
- `app/src/main/java/com/example/data/local/entity/EvidenceEntity.kt`
- `app/src/main/java/com/example/data/local/entity/SyncOperationEntity.kt`
- `app/src/main/java/com/example/data/local/dao/ReferenceDataDao.kt`
- `app/src/main/java/com/example/data/local/dao/ShiftDao.kt`
- `app/src/main/java/com/example/data/local/dao/InspectionDao.kt`
- `app/src/main/java/com/example/data/local/dao/JobDao.kt`
- `app/src/main/java/com/example/data/local/dao/EvidenceDao.kt`
- `app/src/main/java/com/example/data/local/dao/SyncOperationDao.kt`

### New repository/bootstrap files

- `app/src/main/java/com/example/data/repository/RoomDriverRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomShiftRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomInspectionRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomJobRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomEvidenceRepository.kt`
- `app/src/main/java/com/example/data/repository/RoomSyncRepository.kt`
- `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- `app/src/main/java/com/example/AppContainer.kt`
- `app/src/main/java/com/example/FirstClassExpressApplication.kt`
- `app/src/main/java/com/example/viewmodel/ViewModelFactories.kt`

### New feature ViewModels

- `app/src/main/java/com/example/viewmodel/ShiftViewModel.kt`
- `app/src/main/java/com/example/viewmodel/InspectionViewModel.kt`
- `app/src/main/java/com/example/viewmodel/JobViewModel.kt`
- `app/src/main/java/com/example/viewmodel/EvidenceViewModel.kt`

### New tests

- `app/src/test/java/com/example/AppIdentityTest.kt`
- `app/src/test/java/com/example/domain/rules/JobTransitionRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/InspectionRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/ShiftRulesTest.kt`
- `app/src/test/java/com/example/domain/rules/EvidenceRulesTest.kt`
- `app/src/test/java/com/example/data/local/AppDatabaseTest.kt`
- `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`
- `app/src/test/java/com/example/viewmodel/ShiftViewModelTest.kt`
- `app/src/test/java/com/example/viewmodel/JobViewModelTest.kt`
- `app/src/test/java/com/example/viewmodel/EvidenceViewModelTest.kt`

---

### Task 1: Stabilize the Build and Remove Generated Template Debt

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/example/AppIdentityTest.kt`
- Delete: `.env.example`
- Delete: `app/src/test/java/com/example/ExampleUnitTest.kt`
- Delete: `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- Delete: `app/src/test/java/com/example/GreetingScreenshotTest.kt`
- Modify: `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

**Interfaces:**
- Consumes: current Gradle catalog and Android module configuration.
- Produces: standard Android debug signing, no unused Gemini/Firebase AI dependency chain, no stale generated tests, and backup disabled for future operational data.

- [ ] **Step 1: Add a real app-identity unit test**

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

- [ ] **Step 2: Run the current unit-test compilation to record the baseline**

```bash
gradle :app:testDebugUnitTest
```

Expected before cleanup: generated/template sources may fail because `Greeting()` does not exist and stale app expectations do not match the repository.

- [ ] **Step 3: Remove custom debug signing**

Delete `signingConfigs.create("debugConfig")` and delete the `debug { signingConfig = ... }` override from `app/build.gradle.kts`. Keep release signing environment-based. Android then uses its standard per-user debug keystore instead of requiring `${rootDir}/debug.keystore`.

- [ ] **Step 4: Remove the unused AI/Firebase generation scaffold**

From `app/build.gradle.kts` remove:

```kotlin
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
alias(libs.plugins.google.services)
alias(libs.plugins.secrets)
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.ai)
implementation(libs.firebase.appcheck.recaptcha)
```

Also remove the `secrets { ... }` and `googleServices { ... }` blocks.

From root `build.gradle.kts`, remove the unused `secrets` and `google-services` plugin aliases. From `gradle/libs.versions.toml`, remove the Firebase AI/App Check/BOM, Secrets Gradle plugin and Google Services entries that become unreferenced. Delete `.env.example` because its only content is the unused Gemini key placeholder.

Do not remove CameraX, Maps, Location, Retrofit, Moshi or Room dependencies; those remain part of the planned driver-app stack even where their production wiring is deferred.

- [ ] **Step 5: Disable uncontrolled Android backup**

Set:

```xml
android:allowBackup="false"
```

and remove `android:dataExtractionRules` plus `android:fullBackupContent` from the `<application>` element while backup is disabled.

- [ ] **Step 6: Replace the instrumentation template assertion**

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
    fun applicationIdMatchesPrototypePackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aistudio.firstclassexpress.abcde", context.packageName)
    }
}
```

Delete the three generated unit/screenshot tests listed above rather than retaining meaningless assertions.

- [ ] **Step 7: Run the cleaned baseline**

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Expected: test sources compile, `AppIdentityTest` passes, and the debug APK no longer requires a repository-local debug keystore.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "build: stabilize Android project baseline"
```

---

### Task 2: Add Domain Models and Safety-Critical Rules

**Files:**
- Create: `app/src/main/java/com/example/domain/model/OperationalModels.kt`
- Create: `app/src/main/java/com/example/domain/model/InspectionChecklist.kt`
- Create: `app/src/main/java/com/example/domain/rules/JobTransitionRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/InspectionRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/ShiftRules.kt`
- Create: `app/src/main/java/com/example/domain/rules/EvidenceRules.kt`
- Create: `app/src/test/java/com/example/domain/rules/JobTransitionRulesTest.kt`
- Create: `app/src/test/java/com/example/domain/rules/InspectionRulesTest.kt`
- Create: `app/src/test/java/com/example/domain/rules/ShiftRulesTest.kt`
- Create: `app/src/test/java/com/example/domain/rules/EvidenceRulesTest.kt`

**Interfaces:**
- Consumes: `com.example.model.JobStatus`.
- Produces pure Kotlin domain types and rule functions with no Android/Room dependency.

- [ ] **Step 1: Write failing job-transition tests**

```kotlin
package com.example.domain.rules

import com.example.model.JobStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobTransitionRulesTest {
    @Test fun unassignedJobCannotStart() =
        assertFalse(JobTransitionRules.canTransition(JobStatus.UNASSIGNED, JobStatus.IN_PROGRESS))

    @Test fun assignedJobCanStart() =
        assertTrue(JobTransitionRules.canTransition(JobStatus.ASSIGNED, JobStatus.IN_PROGRESS))

    @Test fun jobCannotSkipPickupStages() =
        assertFalse(JobTransitionRules.canTransition(JobStatus.IN_PROGRESS, JobStatus.AT_DELIVERY))

    @Test fun deliveryCanCompleteOnlyFromAtDelivery() =
        assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.COMPLETED))
}
```

- [ ] **Step 2: Write failing inspection-rule tests**

```kotlin
package com.example.domain.rules

import com.example.domain.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionRulesTest {
    @Test
    fun unansweredMandatoryItemBlocksCompletion() {
        val answers = listOf(
            InspectionAnswer("tyres", true, InspectionItemStatus.UNANSWERED)
        )
        assertTrue(InspectionRules.validate(answers, true) is ValidationResult.Invalid)
    }

    @Test
    fun criticalDefectBlocksVehicleReadiness() {
        val answers = listOf(
            InspectionAnswer(
                itemCode = "brakes",
                mandatory = true,
                status = InspectionItemStatus.DEFECT,
                defectDescription = "Brake pedal drops to floor",
                defectSeverity = DefectSeverity.CRITICAL
            )
        )
        assertTrue(InspectionRules.validate(answers, true) is ValidationResult.Blocked)
    }

    @Test
    fun defectRequiresDescriptionAndSeverity() {
        val answers = listOf(
            InspectionAnswer(
                itemCode = "lights",
                mandatory = true,
                status = InspectionItemStatus.DEFECT,
                defectDescription = "",
                defectSeverity = null
            )
        )
        assertTrue(InspectionRules.validate(answers, true) is ValidationResult.Invalid)
    }
}
```

- [ ] **Step 3: Write failing shift/evidence tests**

```kotlin
package com.example.domain.rules

import com.example.domain.model.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftRulesTest {
    @Test
    fun invalidInspectionCannotBecomeReady() {
        val result = ValidationResult.Invalid(listOf("Inspection incomplete"))
        assertFalse(ShiftRules.canMarkReady(result))
    }

    @Test
    fun onlyReadyShiftWithValidInspectionCanActivate() {
        assertTrue(ShiftRules.canActivate(ShiftPhase.READY_TO_START, ValidationResult.Valid))
        assertFalse(ShiftRules.canActivate(ShiftPhase.PRESTART_REQUIRED, ValidationResult.Valid))
    }
}

class EvidenceRulesTest {
    @Test fun pendingCaptureDoesNotCount() =
        assertFalse(EvidenceRules.isSatisfied(EvidenceStatus.PENDING_CAPTURE))

    @Test fun savedLocalCounts() =
        assertTrue(EvidenceRules.isSatisfied(EvidenceStatus.SAVED_LOCAL))
}
```

- [ ] **Step 4: Run tests and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.*"
```

Expected: FAIL because the domain classes do not exist.

- [ ] **Step 5: Implement exact operational types**

`OperationalModels.kt`:

```kotlin
package com.example.domain.model

import com.example.model.JobStatus

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

data class ShiftRecord(
    val id: String,
    val driverId: String,
    val vehicleId: String,
    val trailerId: String?,
    val startOdometer: Long,
    val endOdometer: Long?,
    val phase: ShiftPhase,
    val createdAt: Long,
    val startedAt: Long?,
    val endedAt: Long?
)

data class InspectionItemRecord(
    val id: String,
    val shiftId: String,
    val code: String,
    val label: String,
    val category: String,
    val mandatory: Boolean,
    val status: InspectionItemStatus,
    val defectDescription: String?,
    val defectSeverity: DefectSeverity?
)

data class EvidenceRecord(
    val id: String,
    val jobId: String,
    val type: EvidenceType,
    val localUri: String?,
    val status: EvidenceStatus,
    val createdAt: Long
)

data class SyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
    val status: SyncStatus
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasons: List<String>) : ValidationResult
    data class Blocked(val reasons: List<String>) : ValidationResult
}

data class AllowedJobAction(val from: JobStatus, val to: JobStatus)
```

- [ ] **Step 6: Implement the full inspection checklist**

`InspectionChecklist.kt` defines immutable checklist items and returns trailer checks only when a trailer is assigned. Include exactly:

```text
Exterior: Tyres, Wheels, Lights, Indicators, Mirrors, Windscreen, Wipers, Body damage, Registration plates
Safety: Seatbelt, Horn, Emergency equipment, Fire extinguisher, Warning triangles, First aid kit
Mechanical: Engine warning lights, Brakes, Steering, Oil/fluid leaks, Fuel level, AdBlue
Trailer: Trailer connection, Air lines, Electrical connection, Trailer lights, Trailer tyres, Doors, Load restraint
```

Every created item starts `UNANSWERED`. `NOT_APPLICABLE` is a driver-selected answer, not an initial value.

- [ ] **Step 7: Implement the exact job state map**

```kotlin
package com.example.domain.rules

import com.example.model.JobStatus

object JobTransitionRules {
    private val allowed = mapOf(
        JobStatus.ASSIGNED to setOf(JobStatus.IN_PROGRESS),
        JobStatus.IN_PROGRESS to setOf(JobStatus.AT_PICKUP, JobStatus.ISSUE),
        JobStatus.AT_PICKUP to setOf(JobStatus.PICKED_UP, JobStatus.ISSUE),
        JobStatus.PICKED_UP to setOf(JobStatus.EN_ROUTE_DELIVERY, JobStatus.ISSUE),
        JobStatus.EN_ROUTE_DELIVERY to setOf(JobStatus.AT_DELIVERY, JobStatus.ISSUE),
        JobStatus.AT_DELIVERY to setOf(JobStatus.COMPLETED, JobStatus.ISSUE)
    )

    fun canTransition(from: JobStatus, to: JobStatus): Boolean =
        allowed[from]?.contains(to) == true

    fun allowedNext(from: JobStatus): Set<JobStatus> = allowed[from].orEmpty()
}
```

`UNASSIGNED`, `COMPLETED` and `ISSUE` have no automatic outgoing transition in Phase 1.

- [ ] **Step 8: Implement inspection, shift and evidence rules**

`InspectionRules.validate` executes these checks in order:

1. declaration must be accepted;
2. every mandatory answer must be other than `UNANSWERED`;
3. every `DEFECT` requires nonblank description and non-null severity;
4. any correctly described `CRITICAL` defect returns `Blocked`;
5. otherwise return `Valid`.

`ShiftRules.canMarkReady(result)` returns true only for `ValidationResult.Valid`.

`ShiftRules.canActivate(phase, result)` returns true only when `phase == READY_TO_START` and `result == Valid`.

`EvidenceRules.isSatisfied(status)` returns true only for `SAVED_LOCAL`, `PENDING_SYNC` or `SYNCED`.

- [ ] **Step 9: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.domain.rules.*"
git add app/src/main/java/com/example/domain app/src/test/java/com/example/domain
git commit -m "feat: add driver workflow domain rules"
```

Expected: all rule tests pass.

---

### Task 3: Introduce Room Persistence and JSON Mapping

**Files:**
- Create: `app/src/main/java/com/example/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/example/data/local/JobPayloadCodec.kt`
- Create: all entity and DAO files listed in the locked structure
- Create: `app/src/test/java/com/example/data/local/AppDatabaseTest.kt`

**Interfaces:**
- Consumes: Task 2 domain enums/records and existing `Job` model.
- Produces focused DAOs plus explicit entity/domain mapping in repository code.

- [ ] **Step 1: Create a failing Room test fixture**

Use Robolectric and `Room.inMemoryDatabaseBuilder`. The test class creates the DB in `@Before` and closes it in `@After`.

Required tests:

```kotlin
@Test fun shiftDraftPersists() = runTest {
    shiftDao.insert(ShiftEntity("s1", "d1", "TRK-01", null, 1000L, null, "PRESTART_REQUIRED", 10L, null, null))
    assertEquals("PRESTART_REQUIRED", shiftDao.getById("s1")!!.phase)
}

@Test fun inspectionAnswerRemainsUnansweredUntilExplicitlyChanged() = runTest {
    inspectionDao.insertItem(InspectionItemEntity("i1", "insp1", "s1", "tyres", "Tyres", "Exterior", true, "UNANSWERED", null, null))
    assertEquals("UNANSWERED", inspectionDao.getItem("i1")!!.status)
}

@Test fun evidenceSavedLocalStatePersists() = runTest {
    evidenceDao.insert(EvidenceEntity("e1", "j1", "PICKUP_PHOTO", "prototype://e1", "SAVED_LOCAL", 20L))
    assertEquals("SAVED_LOCAL", evidenceDao.getById("e1")!!.status)
}

@Test fun syncOperationKeepsOriginalTimestamp() = runTest {
    syncDao.insert(SyncOperationEntity("o1", "JOB", "j1", "STATUS_CHANGE", "{}", 1234L, 0, null, "PENDING"))
    assertEquals(1234L, syncDao.getById("o1")!!.createdAt)
}
```

- [ ] **Step 2: Run the Room test and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.local.AppDatabaseTest"
```

Expected: FAIL because entities/DAOs/database are missing.

- [ ] **Step 3: Define the eight Room entities**

Use these tables and fields:

```text
drivers: id PK, name, email
vehicles: id PK, registration, type
shifts: id PK, driverId, vehicleId, trailerId nullable, startOdometer, endOdometer nullable, phase, createdAt, startedAt nullable, endedAt nullable
inspections: id PK, shiftId UNIQUE, declarationAccepted, validationState nullable, completedAt nullable
inspection_items: id PK, inspectionId, shiftId, code, label, category, mandatory, status, defectDescription nullable, defectSeverity nullable
jobs: id PK, payloadJson, status, updatedAt
evidence: id PK, jobId, type, localUri nullable, status, createdAt
sync_operations: id PK, entityType, entityId, operationType, payloadJson, createdAt, retryCount, lastError nullable, status
```

- [ ] **Step 4: Implement focused DAOs**

Required methods include:

```kotlin
interface ReferenceDataDao {
    suspend fun driverCount(): Int
    suspend fun vehicleCount(): Int
    suspend fun firstDriver(): DriverEntity?
    suspend fun insertDriver(driver: DriverEntity)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)
}

interface ShiftDao {
    fun observeCurrent(): Flow<ShiftEntity?>
    suspend fun getById(id: String): ShiftEntity?
    suspend fun insert(entity: ShiftEntity)
    suspend fun updatePhase(id: String, phase: String, startedAt: Long?): Int
}

interface InspectionDao {
    fun observeItems(shiftId: String): Flow<List<InspectionItemEntity>>
    suspend fun getItems(shiftId: String): List<InspectionItemEntity>
    suspend fun getInspectionForShift(shiftId: String): InspectionEntity?
    suspend fun getItem(id: String): InspectionItemEntity?
    suspend fun insertInspection(entity: InspectionEntity)
    suspend fun insertItems(items: List<InspectionItemEntity>)
    suspend fun updateItem(id: String, status: String, description: String?, severity: String?): Int
    suspend fun updateDeclaration(shiftId: String, accepted: Boolean): Int
    suspend fun markCompleted(shiftId: String, validationState: String, completedAt: Long): Int
}

interface JobDao {
    fun observeAll(): Flow<List<JobEntity>>
    suspend fun count(): Int
    suspend fun getById(id: String): JobEntity?
    suspend fun insertAll(jobs: List<JobEntity>)
    suspend fun updateStatus(id: String, status: String, updatedAt: Long): Int
}

interface EvidenceDao {
    fun observeForJob(jobId: String): Flow<List<EvidenceEntity>>
    suspend fun getById(id: String): EvidenceEntity?
    suspend fun insert(entity: EvidenceEntity)
    suspend fun updateSaved(id: String, uri: String, status: String): Int
    suspend fun deletePending(id: String): Int
}

interface SyncOperationDao {
    fun observePending(): Flow<List<SyncOperationEntity>>
    suspend fun getById(id: String): SyncOperationEntity?
    suspend fun insert(entity: SyncOperationEntity)
    suspend fun updateFailure(id: String, retryCount: Int, error: String): Int
    suspend fun updateStatus(id: String, status: String): Int
}
```

Use actual `@Dao`, `@Query`, `@Insert` annotations and `OnConflictStrategy` appropriate to each method.

- [ ] **Step 5: Add `JobPayloadCodec`**

Use the existing Moshi dependency with `KotlinJsonAdapterFactory`:

```kotlin
class JobPayloadCodec(
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {
    private val adapter = moshi.adapter(Job::class.java)

    fun encode(job: Job): String = adapter.toJson(job)

    fun decode(payloadJson: String, persistedStatus: String): Job =
        requireNotNull(adapter.fromJson(payloadJson)).copy(status = JobStatus.valueOf(persistedStatus))
}
```

The separate `status` column is authoritative so state transitions do not require rewriting the full JSON payload.

- [ ] **Step 6: Create `AppDatabase` version 1**

Register all eight entities and expose all six DAOs. Do not use destructive migration fallback in production construction.

- [ ] **Step 7: Run Room tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.local.AppDatabaseTest"
git add app/src/main/java/com/example/data/local app/src/test/java/com/example/data/local
git commit -m "feat: add durable Room operational database"
```

Expected: Room persistence tests pass.

---

### Task 4: Add Domain Repository Contracts, Room Implementations and Seed-Only Prototype Data

**Files:**
- Create: all repository interface/implementation files listed above
- Create: `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- Create: `app/src/main/java/com/example/AppContainer.kt`
- Create: `app/src/main/java/com/example/FirstClassExpressApplication.kt`
- Create: `app/src/main/java/com/example/viewmodel/ViewModelFactories.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete after migration: `app/src/main/java/com/example/data/MockData.kt`
- Create: `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`

**Interfaces:**
- Domain repository contracts return domain records, never Room entities.

```kotlin
interface DriverRepository {
    suspend fun getPrototypeDriver(): Driver?
}

interface ShiftRepository {
    fun observeCurrentShift(): Flow<ShiftRecord?>
    suspend fun createPreStartDraft(driverId: String, vehicleId: String, trailerId: String?, startOdometer: Long): Result<String>
    suspend fun markReadyToStart(shiftId: String): Result<Unit>
    suspend fun activateShift(shiftId: String): Result<Unit>
    suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit>
}

interface InspectionRepository {
    fun observeItems(shiftId: String): Flow<List<InspectionItemRecord>>
    fun observeDeclaration(shiftId: String): Flow<Boolean>
    suspend fun ensureForShift(shiftId: String, hasTrailer: Boolean): Result<Unit>
    suspend fun saveAnswer(itemId: String, answer: InspectionAnswer): Result<Unit>
    suspend fun setDeclaration(shiftId: String, accepted: Boolean): Result<Unit>
    suspend fun complete(shiftId: String): ValidationResult
    suspend fun currentValidation(shiftId: String): ValidationResult
}

interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    suspend fun getJob(id: String): Job?
    suspend fun transition(id: String, to: JobStatus): Result<JobStatus>
}

interface EvidenceRepository {
    fun observeForJob(jobId: String): Flow<List<EvidenceRecord>>
    suspend fun createPending(jobId: String, type: EvidenceType): Result<String>
    suspend fun markSavedLocal(id: String, localUri: String): Result<Unit>
    suspend fun discardPending(id: String): Result<Unit>
}

interface SyncRepository {
    fun observePending(): Flow<List<SyncOperation>>
    suspend fun enqueue(entityType: String, entityId: String, operationType: String, payloadJson: String): Result<String>
    suspend fun markFailure(id: String, error: String): Result<Unit>
    suspend fun markSynced(id: String): Result<Unit>
}
```

- [ ] **Step 1: Write failing repository tests**

Create deterministic repositories with injectable `clock: () -> Long` and `idGenerator: () -> String`. Required assertions:

```kotlin
@Test fun preStartDraftIsNotOnDuty() = runTest {
    val id = shiftRepository.createPreStartDraft("d1", "TRK-01", null, 1000L).getOrThrow()
    assertEquals(ShiftPhase.PRESTART_REQUIRED, shiftRepository.current(id)!!.phase)
}

@Test fun unassignedJobTransitionIsRejected() = runTest {
    val result = jobRepository.transition("unassigned-job", JobStatus.IN_PROGRESS)
    assertTrue(result.isFailure)
}

@Test fun validJobTransitionQueuesExactlyOneOperation() = runTest {
    jobRepository.transition("assigned-job", JobStatus.IN_PROGRESS).getOrThrow()
    assertEquals(1, syncDao.pendingCountFor("JOB", "assigned-job", "STATUS_CHANGE"))
}

@Test fun pendingEvidenceDoesNotBecomeSavedWithoutUri() = runTest {
    val id = evidenceRepository.createPending("j1", EvidenceType.PICKUP_PHOTO).getOrThrow()
    assertEquals(EvidenceStatus.PENDING_CAPTURE, evidenceRepository.get(id)!!.status)
}
```

Add test-only helper methods on repositories or DAOs where needed; do not expose Room entities through production domain interfaces.

- [ ] **Step 2: Run repository tests and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
```

- [ ] **Step 3: Implement Room repositories with transactional mutation + sync enqueue**

`RoomJobRepository.transition` performs this exact sequence:

1. load current `JobEntity`;
2. parse current status;
3. reject when `JobTransitionRules.canTransition` is false;
4. inside `database.withTransaction`, update the job status and insert one `SyncOperationEntity` with `PENDING` status and the original action timestamp;
5. return the persisted new status.

`RoomEvidenceRepository.markSavedLocal` must require a nonblank URI, then in one transaction update evidence to `SAVED_LOCAL` and enqueue its sync operation.

`RoomShiftRepository.markReadyToStart` and `activateShift` must re-read persisted inspection state and call `ShiftRules`; a caller cannot bypass inspection by invoking these methods directly.

- [ ] **Step 4: Make inspection creation idempotent**

`RoomInspectionRepository.ensureForShift` checks for an existing inspection by shift ID. If absent, create one inspection plus all checklist items from `InspectionChecklist`. All items are inserted as `UNANSWERED`. Trailer items are included only when `hasTrailer == true`.

- [ ] **Step 5: Implement seed-only prototype data**

Move the current `MockData.currentDriver` and `MockData.sampleJobs` values into `PrototypeSeedData`. Seed only when the relevant table count is zero. The second seed call must not overwrite status changes made after the first seed.

Add at least the currently used prototype vehicle IDs to `vehicles`; do not invent operational fleet facts beyond the existing prototype data.

Delete `MockData.kt` after all imports have migrated.

- [ ] **Step 6: Add application-level dependency construction**

`AppContainer` is concrete and manual:

```kotlin
class AppContainer(context: Context) {
    val database = Room.databaseBuilder(context, AppDatabase::class.java, "first-class-express.db").build()
    val syncRepository: SyncRepository = RoomSyncRepository(database)
    val driverRepository: DriverRepository = RoomDriverRepository(database.referenceDataDao())
    val inspectionRepository: InspectionRepository = RoomInspectionRepository(database)
    val shiftRepository: ShiftRepository = RoomShiftRepository(database, inspectionRepository)
    val jobRepository: JobRepository = RoomJobRepository(database, JobPayloadCodec())
    val evidenceRepository: EvidenceRepository = RoomEvidenceRepository(database)
}
```

Pass the shared `SyncOperationDao`/repository into mutating repositories as needed so all queue writes occur inside the same Room database transaction.

`FirstClassExpressApplication` owns one container:

```kotlin
class FirstClassExpressApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

Set `android:name=".FirstClassExpressApplication"` in the manifest.

- [ ] **Step 7: Add idempotent seed verification**

Run seeding twice, change one job status between runs, and assert the second seed does not restore the original status.

- [ ] **Step 8: Run repository tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
git add -A
git commit -m "feat: add local-first repositories and prototype seed"
```

---

### Task 5: Correct the Shift and Pre-Start Workflow

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/ShiftViewModel.kt`
- Create: `app/src/main/java/com/example/viewmodel/InspectionViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ShiftStartScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/PreStartInspectionScreen.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Create: `app/src/test/java/com/example/viewmodel/ShiftViewModelTest.kt`

**Interfaces:**
- Produces the persisted sequence `OFF_DUTY -> PRESTART_REQUIRED -> READY_TO_START -> ON_DUTY`.

- [ ] **Step 1: Write failing ViewModel tests**

Use fake repository implementations backed by `MutableStateFlow`.

Required tests:

```kotlin
@Test fun beginPreStartDoesNotSetOnDuty() = runTest {
    viewModel.beginPreStart("d1", "TRK-01", "", "1000")
    assertEquals(ShiftPhase.PRESTART_REQUIRED, fakeShiftRepository.phase)
}

@Test fun incompleteInspectionCannotBecomeReady() = runTest {
    inspectionViewModel.completeInspection()
    assertNotEquals(ShiftPhase.READY_TO_START, fakeShiftRepository.phase)
}

@Test fun criticalDefectCannotBecomeReady() = runTest {
    fakeInspectionRepository.validation = ValidationResult.Blocked(listOf("Critical defect"))
    inspectionViewModel.completeInspection()
    assertEquals(ShiftPhase.PRESTART_REQUIRED, fakeShiftRepository.phase)
}

@Test fun validInspectionBecomesReadyBeforeOnDuty() = runTest {
    fakeInspectionRepository.validation = ValidationResult.Valid
    inspectionViewModel.completeInspection()
    assertEquals(ShiftPhase.READY_TO_START, fakeShiftRepository.phase)
    inspectionViewModel.activateShift()
    assertEquals(ShiftPhase.ON_DUTY, fakeShiftRepository.phase)
}
```

- [ ] **Step 2: Run tests and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.ShiftViewModelTest"
```

- [ ] **Step 3: Implement `ShiftViewModel.beginPreStart`**

Validate:

- vehicle ID nonblank;
- odometer parses to a non-negative `Long`;
- blank trailer becomes `null`.

Create the draft through `ShiftRepository`, then call `InspectionRepository.ensureForShift`. Emit navigation to the inspection only after both calls succeed. Never set `ON_DUTY` here.

- [ ] **Step 4: Implement `InspectionViewModel`**

Expose `StateFlow<InspectionUiState>` built from persisted item/declaration flows. `setAnswer` saves immediately to Room. `completeInspection()` calls `InspectionRepository.complete`; only `Valid` may call `ShiftRepository.markReadyToStart`. It does not activate the shift.

`activateShift()` is a separate action visible only when the persisted phase is `READY_TO_START`.

- [ ] **Step 5: Rewrite `ShiftStartScreen`**

Replace the current direct call:

```kotlin
viewModel.startShift(vehicleId, odometer)
onNavigateToInspection()
```

with `ShiftViewModel.beginPreStart(...)`. Navigation follows a success event from persisted draft creation.

- [ ] **Step 6: Rewrite `PreStartInspectionScreen`**

Remove local per-item `remember` state. Render values from `InspectionUiState`. Each item has:

```text
PASS | DEFECT | N/A
```

When `DEFECT` is selected, show required severity and description controls. The summary area displays unanswered count and blocking reasons.

After a valid completion, show a distinct `START SHIFT` action. A back press before that action leaves the shift `PRESTART_REQUIRED` or `READY_TO_START`, never `ON_DUTY`.

- [ ] **Step 7: Remove old `AppViewModel.startShift`**

Once navigation uses the feature ViewModels, delete the unrestricted shift mutation method. `AppViewModel` may observe the repository to render aggregate home state, but it cannot mutate shift safety state directly.

- [ ] **Step 8: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.ShiftViewModelTest"
git add -A
git commit -m "fix: enforce pre-start before shift activation"
```

---

### Task 6: Move Job Progression Authority into Domain/Repository Logic

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/JobViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/JobsListScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Create: `app/src/test/java/com/example/viewmodel/JobViewModelTest.kt`

**Interfaces:**

```kotlin
data class JobUiState(
    val job: Job? = null,
    val allowedNextStatuses: Set<JobStatus> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

- [ ] **Step 1: Write failing JobViewModel tests**

```kotlin
@Test fun unassignedJobHasNoStartAction() = runTest {
    fakeRepository.current = sampleJob(status = JobStatus.UNASSIGNED)
    viewModel.observeJob(fakeRepository.current.id)
    assertFalse(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.IN_PROGRESS))
}

@Test fun assignedJobCanOnlyStartNormally() = runTest {
    fakeRepository.current = sampleJob(status = JobStatus.ASSIGNED)
    viewModel.observeJob(fakeRepository.current.id)
    assertTrue(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.IN_PROGRESS))
    assertFalse(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.AT_DELIVERY))
}

@Test fun repositoryRejectionDoesNotChangeDisplayedStatus() = runTest {
    fakeRepository.rejectTransitions = true
    viewModel.requestTransition(JobStatus.AT_DELIVERY)
    assertNotNull(viewModel.uiState.value.errorMessage)
}
```

- [ ] **Step 2: Run tests and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.JobViewModelTest"
```

- [ ] **Step 3: Implement `JobViewModel`**

The ViewModel calculates `allowedNextStatuses` with `JobTransitionRules.allowedNext(current.status)`. `requestTransition` always calls `JobRepository.transition`; it never updates a copied list directly.

- [ ] **Step 4: Fix the job screens**

Delete the current behavior that groups `UNASSIGNED` and `ASSIGNED` under the same `Start Job` branch. Render actions from `allowedNextStatuses`. On repository rejection, display the error and keep the persisted status unchanged.

- [ ] **Step 5: Remove old unrestricted job mutation**

Delete `AppViewModel.updateJobStatus(jobId, newStatus)` after all callers migrate. Home/Jobs aggregate lists observe `JobRepository.observeJobs()`.

- [ ] **Step 6: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.JobViewModelTest"
git add -A
git commit -m "fix: validate driver job state transitions"
```

---

### Task 7: Make Photo and Signature Completion Depend on Persisted Evidence

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/EvidenceViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/Workflows.kt`
- Modify: `app/src/main/java/com/example/ui/screens/EvidenceScreens.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Create: `app/src/test/java/com/example/viewmodel/EvidenceViewModelTest.kt`

**Interfaces:**

```kotlin
sealed interface CaptureResult {
    data class Saved(val localUri: String) : CaptureResult
    data object Cancelled : CaptureResult
}
```

`EvidenceViewModel` provides:

```kotlin
suspend fun beginCapture(jobId: String, type: EvidenceType): Result<String>
suspend fun applyCaptureResult(evidenceId: String, result: CaptureResult): Result<Unit>
fun isRequirementSatisfied(records: List<EvidenceRecord>, type: EvidenceType): Boolean
```

- [ ] **Step 1: Write failing evidence tests**

```kotlin
@Test fun cancelledPhotoDoesNotSatisfyRequirement() = runTest {
    val evidenceId = viewModel.beginCapture("j1", EvidenceType.PICKUP_PHOTO).getOrThrow()
    viewModel.applyCaptureResult(evidenceId, CaptureResult.Cancelled).getOrThrow()
    assertFalse(viewModel.isRequirementSatisfied(fakeRepository.records, EvidenceType.PICKUP_PHOTO))
}

@Test fun savedPhotoSatisfiesRequirementOnlyAfterRepositorySave() = runTest {
    val evidenceId = viewModel.beginCapture("j1", EvidenceType.PICKUP_PHOTO).getOrThrow()
    assertFalse(viewModel.isRequirementSatisfied(fakeRepository.records, EvidenceType.PICKUP_PHOTO))
    viewModel.applyCaptureResult(evidenceId, CaptureResult.Saved("prototype://j1/photo-1")).getOrThrow()
    assertTrue(viewModel.isRequirementSatisfied(fakeRepository.records, EvidenceType.PICKUP_PHOTO))
}
```

Add equivalent signature cancellation/save coverage.

- [ ] **Step 2: Run tests and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.EvidenceViewModelTest"
```

- [ ] **Step 3: Implement `EvidenceViewModel`**

`beginCapture` persists `PENDING_CAPTURE`. `Saved` calls `markSavedLocal`; `Cancelled` calls `discardPending`. Satisfaction uses `EvidenceRules.isSatisfied` over repository records.

- [ ] **Step 4: Fix workflow screens**

Remove all optimistic patterns equivalent to:

```kotlin
hasPhoto = true
hasSignature = true
```

before navigation. Pickup/delivery workflow checkmarks are derived from persisted evidence records.

- [ ] **Step 5: Make prototype evidence explicit**

Because real CameraX/signature-file output is Phase 2, the existing prototype screens return a `prototype://...` URI only after the user explicitly confirms Save. Cancelling/back returns `CaptureResult.Cancelled`. Add a visible `Prototype capture` label so this phase does not misrepresent placeholder evidence as production POD.

- [ ] **Step 6: Run tests and commit**

```bash
gradle :app:testDebugUnitTest --tests "com.example.viewmodel.EvidenceViewModelTest"
git add -A
git commit -m "fix: require persisted proof before workflow completion"
```

---

### Task 8: Complete App-State Migration and Seed Through Room

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/java/com/example/viewmodel/AppViewModel.kt`
- Modify: `app/src/main/java/com/example/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/data/seed/PrototypeSeedData.kt`
- Modify: `app/src/main/java/com/example/viewmodel/ViewModelFactories.kt`
- Delete: `app/src/main/java/com/example/data/MockData.kt` if not already removed
- Extend: `app/src/test/java/com/example/data/repository/RoomRepositoriesTest.kt`

**Interfaces:**
- Consumes: `FirstClassExpressApplication.container` and repository flows.
- Produces: one startup path where prototype content is seeded once and operational screens observe Room-backed repositories.

- [ ] **Step 1: Add seed-idempotency test**

Test sequence:

1. call `seedIfEmpty()`;
2. transition one seeded job to the next valid status;
3. call `seedIfEmpty()` again;
4. assert job count is unchanged and the transitioned status remains changed.

- [ ] **Step 2: Run the seed test and confirm failure**

```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.RoomRepositoriesTest"
```

- [ ] **Step 3: Wire `MainActivity` to the application container**

Obtain:

```kotlin
val container = (application as FirstClassExpressApplication).container
```

Build feature ViewModels through `ViewModelFactories.kt`; composables do not construct databases or repositories.

- [ ] **Step 4: Keep temporary prototype login but remove direct mock imports**

`AppViewModel.login` may continue accepting any nonblank prototype credentials in Phase 1, but on success it loads the seeded driver through `DriverRepository` and observes job/shift repositories. It cannot import `MockData` or directly mutate operational status.

- [ ] **Step 5: Search for unsafe legacy paths**

```bash
git grep -n "MockData" -- app/src/main/java || true
git grep -n "updateJobStatus" -- app/src/main/java || true
git grep -n "fun startShift" -- app/src/main/java || true
git grep -n 'mutableStateOf<String?>("PASS")' -- app/src/main/java || true
git grep -n "hasPhoto = true\|hasSignature = true" -- app/src/main/java || true
```

Expected: no unsafe legacy matches.

- [ ] **Step 6: Run the complete unit suite and commit**

```bash
gradle :app:testDebugUnitTest
git add -A
git commit -m "refactor: make Room the operational source of truth"
```

---

### Task 9: Final Phase 1 Verification

**Files:**
- Modify only files necessary to fix verification failures caused by Tasks 1-8.
- Modify: `README.md` if it still instructs users to configure Gemini/AI secrets that the app no longer uses.

**Interfaces:**
- Produces: a verified `main` branch meeting the approved design acceptance criteria.

- [ ] **Step 1: Run all unit tests**

```bash
gradle :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Compile the debug APK**

```bash
gradle :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` without a repository-local `debug.keystore`.

- [ ] **Step 3: Compile instrumentation tests**

```bash
gradle :app:assembleDebugAndroidTest
```

Expected: test APK compiles against `com.aistudio.firstclassexpress.abcde`.

- [ ] **Step 4: Run targeted safety-rule tests**

```bash
gradle :app:testDebugUnitTest \
  --tests "com.example.domain.rules.JobTransitionRulesTest" \
  --tests "com.example.domain.rules.InspectionRulesTest" \
  --tests "com.example.domain.rules.ShiftRulesTest" \
  --tests "com.example.domain.rules.EvidenceRulesTest" \
  --tests "com.example.viewmodel.ShiftViewModelTest" \
  --tests "com.example.viewmodel.JobViewModelTest" \
  --tests "com.example.viewmodel.EvidenceViewModelTest"
```

Expected: PASS.

- [ ] **Step 5: Verify unsafe/generated patterns are gone**

```bash
git grep -n "debugConfig" -- app/build.gradle.kts || true
git grep -n "firebase.ai\|firebase-ai\|GEMINI_API_KEY" -- . ':!docs' || true
git grep -n 'mutableStateOf<String?>("PASS")' -- app/src/main/java || true
git grep -n "JobStatus.UNASSIGNED, JobStatus.ASSIGNED" -- app/src/main/java || true
git grep -n "hasPhoto = true\|hasSignature = true" -- app/src/main/java || true
```

Expected: no matches for the old unsafe/generated patterns.

- [ ] **Step 6: Check diff quality**

```bash
git status
git diff --check
git diff
```

Fix only defects directly related to Phase 1, rerun affected tests, and commit any verification corrections with:

```bash
git add -A
git commit -m "fix: close phase 1 verification gaps"
```

Skip this commit when verification needs no changes.

- [ ] **Step 7: Confirm clean repository state**

```bash
git status --short
git log --oneline -12
```

Expected: clean working tree and small Phase 1 commits on `main`.

---

## Phase 1 Acceptance Checklist

- [ ] Debug builds do not depend on a checked-in `debug.keystore`.
- [ ] Generated template tests no longer reference nonexistent or unrelated app code.
- [ ] The driver cannot become `ON_DUTY` before a valid pre-start inspection and explicit final Start Shift action.
- [ ] Mandatory inspection items begin `UNANSWERED` and require an explicit `PASS`, `DEFECT` or `N/A` response.
- [ ] Defects require description and severity; `CRITICAL` blocks shift readiness/activation.
- [ ] `UNASSIGNED` jobs cannot start and invalid status skips are rejected by domain/repository logic.
- [ ] Camera/signature navigation cannot falsely satisfy evidence requirements.
- [ ] Shift, inspection, job, evidence and sync state persist in Room.
- [ ] Every syncable local mutation queues durable pending work without falsely marking it remotely synced.
- [ ] Prototype seed data runs only on empty tables and does not overwrite later local state.
- [ ] The existing UI remains recognizable and navigable.
- [ ] Unit tests, debug APK compilation and instrumentation-test compilation succeed.
