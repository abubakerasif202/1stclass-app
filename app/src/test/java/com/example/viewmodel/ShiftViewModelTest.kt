package com.example.viewmodel

import com.example.domain.model.InspectionAnswer
import com.example.domain.model.InspectionItemRecord
import com.example.domain.model.ShiftPhase
import com.example.domain.model.ShiftRecord
import com.example.domain.model.ValidationResult
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShiftViewModelTest {
    @Test
    fun beginPreStartDoesNotSetOnDuty() = runTest {
        val shiftRepository = FakeShiftRepository()
        val inspectionRepository = FakeInspectionRepository()
        val viewModel = ShiftViewModel(shiftRepository, inspectionRepository)

        viewModel.beginPreStart("d1", "TRK-01", "", "1000").getOrThrow()

        assertEquals(ShiftPhase.PRESTART_REQUIRED, shiftRepository.phase)
    }

    @Test
    fun incompleteInspectionCannotBecomeReady() = runTest {
        val shiftRepository = FakeShiftRepository(ShiftPhase.PRESTART_REQUIRED)
        val inspectionRepository = FakeInspectionRepository(
            ValidationResult.Invalid(listOf("Inspection incomplete"))
        )
        val viewModel = InspectionViewModel("s1", inspectionRepository, shiftRepository)

        viewModel.completeInspection()

        assertNotEquals(ShiftPhase.READY_TO_START, shiftRepository.phase)
    }

    @Test
    fun criticalDefectCannotBecomeReady() = runTest {
        val shiftRepository = FakeShiftRepository(ShiftPhase.PRESTART_REQUIRED)
        val inspectionRepository = FakeInspectionRepository(
            ValidationResult.Blocked(listOf("Critical defect"))
        )
        val viewModel = InspectionViewModel("s1", inspectionRepository, shiftRepository)

        viewModel.completeInspection()

        assertEquals(ShiftPhase.PRESTART_REQUIRED, shiftRepository.phase)
    }

    @Test
    fun validInspectionBecomesReadyBeforeOnDuty() = runTest {
        val shiftRepository = FakeShiftRepository(ShiftPhase.PRESTART_REQUIRED)
        val inspectionRepository = FakeInspectionRepository(ValidationResult.Valid)
        val viewModel = InspectionViewModel("s1", inspectionRepository, shiftRepository)

        viewModel.completeInspection()
        assertEquals(ShiftPhase.READY_TO_START, shiftRepository.phase)
        viewModel.activateShift().getOrThrow()
        assertEquals(ShiftPhase.ON_DUTY, shiftRepository.phase)
    }

    private class FakeShiftRepository(
        initialPhase: ShiftPhase = ShiftPhase.OFF_DUTY
    ) : ShiftRepository {
        var phase: ShiftPhase = initialPhase
        private val flow = MutableStateFlow<ShiftRecord?>(record(initialPhase))

        override fun observeCurrentShift(): Flow<ShiftRecord?> = flow

        override suspend fun createPreStartDraft(
            driverId: String,
            vehicleId: String,
            trailerId: String?,
            startOdometer: Long
        ): Result<String> {
            phase = ShiftPhase.PRESTART_REQUIRED
            flow.value = record(phase)
            return Result.success("s1")
        }

        override suspend fun markReadyToStart(shiftId: String): Result<Unit> {
            phase = ShiftPhase.READY_TO_START
            flow.value = record(phase)
            return Result.success(Unit)
        }

        override suspend fun activateShift(shiftId: String): Result<Unit> {
            if (phase != ShiftPhase.READY_TO_START) return Result.failure(IllegalStateException("Not ready"))
            phase = ShiftPhase.ON_DUTY
            flow.value = record(phase)
            return Result.success(Unit)
        }

        override suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit> {
            phase = ShiftPhase.OFF_DUTY
            flow.value = null
            return Result.success(Unit)
        }

        companion object {
            private fun record(phase: ShiftPhase) = ShiftRecord(
                id = "s1",
                driverId = "d1",
                vehicleId = "TRK-01",
                trailerId = null,
                startOdometer = 1000L,
                endOdometer = null,
                phase = phase,
                createdAt = 1L,
                startedAt = null,
                endedAt = null
            )
        }
    }

    private class FakeInspectionRepository(
        var validation: ValidationResult = ValidationResult.Invalid(listOf("Incomplete"))
    ) : InspectionRepository {
        override fun observeItems(shiftId: String): Flow<List<InspectionItemRecord>> = MutableStateFlow(emptyList())
        override fun observeDeclaration(shiftId: String): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun ensureForShift(shiftId: String, hasTrailer: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun saveAnswer(itemId: String, answer: InspectionAnswer): Result<Unit> = Result.success(Unit)
        override suspend fun setDeclaration(shiftId: String, accepted: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun complete(shiftId: String): ValidationResult = validation
        override suspend fun currentValidation(shiftId: String): ValidationResult = validation
    }
}
