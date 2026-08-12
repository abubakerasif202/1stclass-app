package com.example.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.AppContainer
import com.example.domain.model.ShiftPhase
import com.example.ui.screens.*
import com.example.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
    container: AppContainer
) {
    val uiState by viewModel.uiState.collectAsState()
    val shiftViewModel: ShiftViewModel = viewModel(
        factory = viewModelFactory { ShiftViewModel(container.shiftRepository, container.inspectionRepository) }
    )
    val jobViewModel: JobViewModel = viewModel(
        factory = viewModelFactory { JobViewModel(container.jobRepository) }
    )
    val evidenceViewModel: EvidenceViewModel = viewModel(
        factory = viewModelFactory { EvidenceViewModel(container.evidenceRepository) }
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isMainScreen = BottomNavScreens.any { it.route == currentDestination?.route }
            if (uiState.isLoggedIn && isMainScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    listOf(
                        BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home),
                        BottomNavItem("Jobs", Screen.JobsList.route, Icons.AutoMirrored.Filled.List),
                        BottomNavItem("Map", Screen.Map.route, Icons.Filled.LocationOn),
                        BottomNavItem("Messages", Screen.Messages.route, Icons.Filled.Email),
                        BottomNavItem("More", Screen.More.route, Icons.Filled.Menu)
                    ).forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.name) },
                            label = { Text(item.name) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(viewModel) {
                    navController.navigate(Screen.Home.route) { popUpTo(0) }
                }
            }
            composable(Screen.Home.route) {
                val shiftState by shiftViewModel.uiState.collectAsState()
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToShiftStart = {
                        val current = shiftState.currentShift
                        if (current != null && current.phase in setOf(ShiftPhase.PRESTART_REQUIRED, ShiftPhase.READY_TO_START)) {
                            navController.navigate(Screen.PreStartInspection.createRoute(current.id))
                        } else {
                            navController.navigate(Screen.ShiftStart.route)
                        }
                    },
                    onNavigateToJobs = { navController.navigate(Screen.JobsList.route) },
                    onNavigateToJobDetail = { navController.navigate(Screen.JobDetail.createRoute(it)) }
                )
            }
            composable(Screen.ShiftStart.route) {
                ShiftStartScreen(
                    viewModel = shiftViewModel,
                    driverId = uiState.driver?.id.orEmpty(),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInspection = { navController.navigate(Screen.PreStartInspection.createRoute(it)) }
                )
            }
            composable(Screen.PreStartInspection.route) { entry ->
                val shiftId = entry.arguments?.getString("shiftId") ?: return@composable
                val inspectionViewModel: InspectionViewModel = viewModel(
                    key = "inspection-$shiftId",
                    factory = viewModelFactory {
                        InspectionViewModel(shiftId, container.inspectionRepository, container.shiftRepository)
                    }
                )
                PreStartInspectionScreen(inspectionViewModel) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            composable(Screen.JobsList.route) {
                JobsListScreen(viewModel) { navController.navigate(Screen.JobDetail.createRoute(it)) }
            }
            composable(Screen.JobDetail.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                JobDetailScreen(
                    viewModel = jobViewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPickup = { navController.navigate(Screen.Pickup.createRoute(jobId)) },
                    onNavigateToDelivery = { navController.navigate(Screen.Delivery.createRoute(jobId)) },
                    onNavigateToMap = { navController.navigate(Screen.Map.route) }
                )
            }
            composable(Screen.Pickup.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                PickupScreen(
                    jobViewModel = jobViewModel,
                    evidenceViewModel = evidenceViewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { evidenceId ->
                        navController.navigate(Screen.CameraCapture.createRoute(jobId, "pickup", evidenceId))
                    },
                    onNavigateToSignature = { evidenceId ->
                        navController.navigate(Screen.SignatureCapture.createRoute(jobId, evidenceId, "pickup"))
                    },
                    onPickupComplete = { navController.popBackStack() }
                )
            }
            composable(Screen.Delivery.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                DeliveryScreen(
                    jobViewModel = jobViewModel,
                    evidenceViewModel = evidenceViewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { evidenceId ->
                        navController.navigate(Screen.CameraCapture.createRoute(jobId, "delivery", evidenceId))
                    },
                    onNavigateToSignature = { evidenceId ->
                        navController.navigate(Screen.SignatureCapture.createRoute(jobId, evidenceId, "delivery"))
                    },
                    onDeliveryComplete = { navController.popBackStack() }
                )
            }
            composable(Screen.SignatureCapture.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                val evidenceId = entry.arguments?.getString("evidenceId") ?: return@composable
                val scope = rememberCoroutineScope()
                SignatureScreen(
                    jobId = jobId,
                    evidenceId = evidenceId,
                    onCancel = {
                        scope.launch {
                            evidenceViewModel.applyCaptureResult(evidenceId, CaptureResult.Cancelled)
                            navController.popBackStack()
                        }
                    },
                    onSignatureSaved = { uri ->
                        scope.launch {
                            evidenceViewModel.applyCaptureResult(evidenceId, CaptureResult.Saved(uri))
                                .onSuccess { navController.popBackStack() }
                        }
                    }
                )
            }
            composable(Screen.CameraCapture.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                val type = entry.arguments?.getString("type") ?: "evidence"
                val evidenceId = entry.arguments?.getString("evidenceId") ?: return@composable
                val scope = rememberCoroutineScope()
                CameraScreen(
                    jobId = jobId,
                    evidenceId = evidenceId,
                    type = type,
                    onCancel = {
                        scope.launch {
                            evidenceViewModel.applyCaptureResult(evidenceId, CaptureResult.Cancelled)
                            navController.popBackStack()
                        }
                    },
                    onPhotoSaved = { uri ->
                        scope.launch {
                            evidenceViewModel.applyCaptureResult(evidenceId, CaptureResult.Saved(uri))
                                .onSuccess { navController.popBackStack() }
                        }
                    }
                )
            }
            composable(Screen.Map.route) { MapScreen(viewModel) }
            composable(Screen.Messages.route) { MessagesScreen(viewModel) }
            composable(Screen.More.route) {
                MoreScreen(viewModel, shiftViewModel) {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) { popUpTo(0) }
                }
            }
        }
    }
}

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)
