package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.model.ValidationResult

object ShiftRules {
    fun canMarkReady(result: ValidationResult): Boolean = result == ValidationResult.Valid

    fun canActivate(phase: ShiftPhase, result: ValidationResult): Boolean =
        phase == ShiftPhase.READY_TO_START && result == ValidationResult.Valid
}
