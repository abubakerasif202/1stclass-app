package com.example.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object ShiftStart : Screen("shift_start")
    object PreStartInspection : Screen("pre_start_inspection/{shiftId}") {
        fun createRoute(shiftId: String) = "pre_start_inspection/$shiftId"
    }

    object JobsList : Screen("jobs_list")
    object Map : Screen("map")
    object Messages : Screen("messages")
    object More : Screen("more")

    /** Sync diagnostics, reached from the dashboard indicator and from Profile. */
    object SyncDetails : Screen("sync_details")

    object JobDetail : Screen("job_detail/{jobId}") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    object Pickup : Screen("pickup/{jobId}") {
        fun createRoute(jobId: String) = "pickup/$jobId"
    }
    object Delivery : Screen("delivery/{jobId}") {
        fun createRoute(jobId: String) = "delivery/$jobId"
    }
    /**
     * Capture routes carry only the evidence id — the pending record in Room holds job, type and
     * driver, so a capture screen survives process death without stale route arguments.
     */
    object SignatureCapture : Screen("signature/{evidenceId}/{stage}") {
        fun createRoute(evidenceId: String, stage: String) = "signature/$evidenceId/$stage"
    }
    object CameraCapture : Screen("camera/{evidenceId}/{stage}") {
        fun createRoute(evidenceId: String, stage: String) = "camera/$evidenceId/$stage"
    }
}

val BottomNavScreens = listOf(
    Screen.Home,
    Screen.JobsList,
    Screen.Map,
    Screen.Messages,
    Screen.More
)
