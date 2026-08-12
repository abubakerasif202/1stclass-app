package com.example.domain.rules

import com.example.domain.model.DefectSeverity
import com.example.domain.model.InspectionAnswer
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.ValidationResult
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionRulesTest {
    @Test
    fun unansweredMandatoryItemBlocksCompletion() {
        val answers = listOf(InspectionAnswer("tyres", true, InspectionItemStatus.UNANSWERED))
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
