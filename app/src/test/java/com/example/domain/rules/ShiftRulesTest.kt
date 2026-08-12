package com.example.domain.rules

import com.example.domain.model.ShiftPhase
import com.example.domain.model.ValidationResult
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
