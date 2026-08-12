package com.example.domain.rules

import com.example.domain.model.ShiftPhase
import com.example.domain.model.ValidationResult

object ShiftRules {
    fun canMarkReady(result: ValidationResult): Boolean = result == ValidationResult.Valid

    fun canActivate(phase: ShiftPhase, result: ValidationResult): Boolean =
        phase == ShiftPhase.READY_TO_START && result == ValidationResult.Valid
}
