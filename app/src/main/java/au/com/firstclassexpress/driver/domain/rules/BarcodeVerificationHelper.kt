package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.model.Job

data class BarcodeMatchResult(
    val isMatched: Boolean,
    val matchType: String,
    val matchedReference: String,
    val message: String
)

object BarcodeVerificationHelper {

    fun verifyScannedCode(scannedCode: String, job: Job): BarcodeMatchResult {
        val sanitized = scannedCode.trim().uppercase()
        if (sanitized.isBlank()) {
            return BarcodeMatchResult(
                isMatched = false,
                matchType = "EMPTY",
                matchedReference = "",
                message = "No barcode or consignment reference detected."
            )
        }

        val ref = job.reference.trim().uppercase()
        val id = job.id.trim().uppercase()

        // 1. Direct match on Job Reference
        if (sanitized == ref || sanitized.contains(ref) || ref.contains(sanitized)) {
            return BarcodeMatchResult(
                isMatched = true,
                matchType = "REFERENCE_MATCH",
                matchedReference = job.reference,
                message = "Consignment reference verified: ${job.reference} (${job.itemCount} items)"
            )
        }

        // 2. Direct match on Job ID
        if (sanitized == id) {
            return BarcodeMatchResult(
                isMatched = true,
                matchType = "ID_MATCH",
                matchedReference = job.reference,
                message = "Job consignment ID verified: ${job.reference}"
            )
        }

        // 3. Substring match in Freight Description or Special Instructions
        val freightUpper = job.freightDescription.uppercase()
        val instructionsUpper = job.specialInstructions.uppercase()
        if (freightUpper.contains(sanitized) || instructionsUpper.contains(sanitized)) {
            return BarcodeMatchResult(
                isMatched = true,
                matchType = "MANIFEST_MATCH",
                matchedReference = job.reference,
                message = "Item identifier matched in manifest description."
            )
        }

        return BarcodeMatchResult(
            isMatched = false,
            matchType = "UNMATCHED",
            matchedReference = sanitized,
            message = "Scanned reference '$sanitized' does not match active job ${job.reference}."
        )
    }
}
