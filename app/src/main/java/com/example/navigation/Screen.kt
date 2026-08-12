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

    object JobDetail : Screen("job_detail/{jobId}") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    object Pickup : Screen("pickup/{jobId}") {
        fun createRoute(jobId: String) = "pickup/$jobId"
    }
    object Delivery : Screen("delivery/{jobId}") {
        fun createRoute(jobId: String) = "delivery/$jobId"
    }
    object SignatureCapture : Screen("signature/{jobId}/{evidenceId}/{type}") {
        fun createRoute(jobId: String, evidenceId: String, type: String) =
            "signature/$jobId/$evidenceId/$type"
    }
    object CameraCapture : Screen("camera/{jobId}/{type}/{evidenceId}") {
        fun createRoute(jobId: String, type: String, evidenceId: String) =
            "camera/$jobId/$type/$evidenceId"
    }
}

val BottomNavScreens = listOf(
    Screen.Home,
    Screen.JobsList,
    Screen.Map,
    Screen.Messages,
    Screen.More
)
