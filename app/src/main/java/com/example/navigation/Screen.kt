package com.example.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object ShiftStart : Screen("shift_start")
    object PreStartInspection : Screen("pre_start_inspection")
    
    // Bottom Nav Screens
    object JobsList : Screen("jobs_list")
    object Map : Screen("map")
    object Messages : Screen("messages")
    object More : Screen("more")
    
    // Job details flows
    object JobDetail : Screen("job_detail/{jobId}") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    object Pickup : Screen("pickup/{jobId}") {
        fun createRoute(jobId: String) = "pickup/$jobId"
    }
    object Delivery : Screen("delivery/{jobId}") {
        fun createRoute(jobId: String) = "delivery/$jobId"
    }
    object SignatureCapture : Screen("signature/{jobId}") {
        fun createRoute(jobId: String) = "signature/$jobId"
    }
    object CameraCapture : Screen("camera/{jobId}/{type}") {
        fun createRoute(jobId: String, type: String) = "camera/$jobId/$type"
    }
}

val BottomNavScreens = listOf(
    Screen.Home,
    Screen.JobsList,
    Screen.Map,
    Screen.Messages,
    Screen.More
)
