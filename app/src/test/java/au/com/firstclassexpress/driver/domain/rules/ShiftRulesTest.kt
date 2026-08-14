package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.model.ValidationResult
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
