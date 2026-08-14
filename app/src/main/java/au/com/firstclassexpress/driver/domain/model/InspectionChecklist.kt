package au.com.firstclassexpress.driver.domain.model

data class InspectionChecklistItem(
    val code: String,
    val label: String,
    val category: String,
    val mandatory: Boolean = true,
    val status: InspectionItemStatus = InspectionItemStatus.UNANSWERED
)

object InspectionChecklist {
    private fun item(category: String, label: String): InspectionChecklistItem =
        InspectionChecklistItem(
            code = "${category.lowercase()}_${label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}",
            label = label,
            category = category
        )

    private val exterior = listOf(
        "Tyres", "Wheels", "Lights", "Indicators", "Mirrors", "Windscreen", "Wipers",
        "Body damage", "Registration plates"
    ).map { item("Exterior", it) }

    private val safety = listOf(
        "Seatbelt", "Horn", "Emergency equipment", "Fire extinguisher", "Warning triangles",
        "First aid kit"
    ).map { item("Safety", it) }

    private val mechanical = listOf(
        "Engine warning lights", "Brakes", "Steering", "Oil/fluid leaks", "Fuel level", "AdBlue"
    ).map { item("Mechanical", it) }

    private val trailer = listOf(
        "Trailer connection", "Air lines", "Electrical connection", "Trailer lights", "Trailer tyres",
        "Doors", "Load restraint"
    ).map { item("Trailer", it) }

    fun items(hasTrailer: Boolean): List<InspectionChecklistItem> =
        if (hasTrailer) exterior + safety + mechanical + trailer else exterior + safety + mechanical
}
