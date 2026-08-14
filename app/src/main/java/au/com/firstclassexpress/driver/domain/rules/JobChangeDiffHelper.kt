package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.model.Job

data class JobChangeSummary(
    val hasChanges: Boolean,
    val changedFields: List<String>,
    val detailLines: List<String>
)

object JobChangeDiffHelper {

    fun compareJobs(previous: Job, current: Job): JobChangeSummary {
        val changedFields = mutableListOf<String>()
        val detailLines = mutableListOf<String>()

        if (previous.delivery.address != current.delivery.address || previous.delivery.suburb != current.delivery.suburb) {
            changedFields.add("Delivery Address")
            detailLines.add("Delivery Address updated to: ${current.delivery.address}, ${current.delivery.suburb}")
        }

        if (previous.deliveryWindowStart != current.deliveryWindowStart || previous.deliveryWindowEnd != current.deliveryWindowEnd) {
            changedFields.add("Delivery Window")
            detailLines.add("Delivery Window changed from [${previous.deliveryWindowStart} - ${previous.deliveryWindowEnd}] to [${current.deliveryWindowStart} - ${current.deliveryWindowEnd}]")
        }

        if (previous.pickupWindowStart != current.pickupWindowStart || previous.pickupWindowEnd != current.pickupWindowEnd) {
            changedFields.add("Pickup Window")
            detailLines.add("Pickup Window changed to: ${current.pickupWindowStart} - ${current.pickupWindowEnd}")
        }

        if (previous.priority != current.priority) {
            changedFields.add("Priority")
            detailLines.add("Priority escalated to: ${current.priority.name}")
        }

        if (previous.specialInstructions != current.specialInstructions && current.specialInstructions.isNotBlank()) {
            changedFields.add("Special Instructions")
            detailLines.add("Instructions updated: ${current.specialInstructions}")
        }

        return JobChangeSummary(
            hasChanges = changedFields.isNotEmpty(),
            changedFields = changedFields,
            detailLines = detailLines
        )
    }
}
