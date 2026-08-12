package com.example.domain.rules

import com.example.domain.model.DefectSeverity
import com.example.domain.model.InspectionAnswer
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.ValidationResult

object InspectionRules {
    fun validate(answers: List<InspectionAnswer>, declarationAccepted: Boolean): ValidationResult {
        if (!declarationAccepted) return ValidationResult.Invalid(listOf("Safety declaration must be accepted"))

        val unanswered = answers.filter { it.mandatory && it.status == InspectionItemStatus.UNANSWERED }
        if (unanswered.isNotEmpty()) {
            return ValidationResult.Invalid(unanswered.map { "${it.itemCode} is unanswered" })
        }

        val incompleteDefects = answers.filter {
            it.status == InspectionItemStatus.DEFECT &&
                (it.defectDescription.isNullOrBlank() || it.defectSeverity == null)
        }
        if (incompleteDefects.isNotEmpty()) {
            return ValidationResult.Invalid(incompleteDefects.map { "${it.itemCode} defect requires description and severity" })
        }

        val critical = answers.filter {
            it.status == InspectionItemStatus.DEFECT && it.defectSeverity == DefectSeverity.CRITICAL
        }
        if (critical.isNotEmpty()) {
            return ValidationResult.Blocked(critical.map { "Critical defect: ${it.itemCode}" })
        }

        return ValidationResult.Valid
    }
}
