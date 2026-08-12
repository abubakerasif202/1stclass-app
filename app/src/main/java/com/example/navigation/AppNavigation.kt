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
import com.example.BuildConfig
import com.example.domain.model.ShiftPhase
import com.example.ui.capture.CameraCaptureScreen
import com.example.ui.capture.SignatureCaptureScreen
import com.example.ui.screens.*
import com.example.viewmodel.*

private const val STAGE_PICKUP = "pickup"
private const val STAGE_DELIVERY = "delivery"

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
    container: AppContainer
) {
    val uiState by viewModel.uiState.collectAsState()
    val shiftViewModel: ShiftViewModel = viewModel(
        factory = viewModelFactory {
            ShiftViewModel(container.shiftRepository, container.inspectionRepository)
        }
    )
    val jobViewModel: JobViewModel = viewModel(
        factory = viewModelFactory { JobViewModel(container.jobRepository) }
    )
    val evidenceViewModel: EvidenceViewModel = viewModel(
        factory = viewModelFactory {
            EvidenceViewModel(container.evidenceCaptureService, container.evidenceRepository)
        }
    )

    val driverId = uiState.driver?.id.orEmpty()
    val shiftId = uiState.currentShiftId

    // Sign-out anywhere in the app returns to the login screen.
    LaunchedEffect(uiState.isLoggedIn, uiState.isRestoringSession) {
        if (!uiState.isRestoringSession && !uiState.isLoggedIn) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != null && current != Screen.Login.route) {
                navController.navigate(Screen.Login.route) { popUpTo(0) }
            }
        }
    }

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
                        BottomNavItem("Profile", Screen.More.route, Icons.Filled.Menu)
                    ).forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.name) },
                            label = { Text(item.name) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
                        val awaitingPreStart = current != null && current.phase in setOf(
                            ShiftPhase.PRESTART_REQUIRED,
                            ShiftPhase.READY_TO_START
                        )
                        if (awaitingPreStart) {
                            navController.navigate(
                                Screen.PreStartInspection.createRoute(current!!.id)
                            )
                        } else {
                            navController.navigate(Screen.ShiftStart.route)
                        }
                    },
                    onNavigateToJobs = { navController.navigate(Screen.JobsList.route) },
                    onNavigateToJobDetail = {
                        navController.navigate(Screen.JobDetail.createRoute(it))
                    }
                )
            }
            composable(Screen.ShiftStart.route) {
                ShiftStartScreen(
                    viewModel = shiftViewModel,
                    driverId = driverId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInspection = {
                        navController.navigate(Screen.PreStartInspection.createRoute(it))
                    }
                )
            }
            composable(Screen.PreStartInspection.route) { entry ->
                val inspectionShiftId = entry.arguments?.getString("shiftId") ?: return@composable
                val inspectionViewModel: InspectionViewModel = viewModel(
                    key = "inspection-$inspectionShiftId",
                    factory = viewModelFactory {
                        InspectionViewModel(
                            inspectionShiftId,
                            container.inspectionRepository,
                            container.shiftRepository
                        )
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
                JobsListScreen(viewModel) {
                    navController.navigate(Screen.JobDetail.createRoute(it))
                }
            }
            composable(Screen.JobDetail.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                val evidence by container.evidenceRepository.observeForJob(jobId)
                    .collectAsState(initial = emptyList())
                val exceptions by container.freightExceptionRepository.observeForJob(jobId)
                    .collectAsState(initial = emptyList())
                JobDetailScreen(
                    viewModel = jobViewModel,
                    jobId = jobId,
                    evidence = evidence,
                    exceptions = exceptions,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPickup = {
                        navController.navigate(Screen.Pickup.createRoute(jobId))
                    },
                    onNavigateToDelivery = {
                        navController.navigate(Screen.Delivery.createRoute(jobId))
                    },
                    onNavigateToMap = { navController.navigate(Screen.Map.route) }
                )
            }
            composable(Screen.Pickup.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                val pickupViewModel: PickupViewModel = viewModel(
                    key = "pickup-$jobId",
                    factory = viewModelFactory {
                        PickupViewModel(
                            jobId = jobId,
                            driverId = driverId,
                            shiftId = shiftId,
                            jobRepository = container.jobRepository,
                            evidenceRepository = container.evidenceRepository,
                            exceptionRepository = container.freightExceptionRepository
                        )
                    }
                )
                PickupScreen(
                    pickupViewModel = pickupViewModel,
                    evidenceViewModel = evidenceViewModel,
                    jobId = jobId,
                    driverId = driverId,
                    shiftId = shiftId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { evidenceId ->
                        navController.navigate(
                            Screen.CameraCapture.createRoute(evidenceId, STAGE_PICKUP)
                        )
                    },
                    onNavigateToSignature = { evidenceId ->
                        navController.navigate(
                            Screen.SignatureCapture.createRoute(evidenceId, STAGE_PICKUP)
                        )
                    },
                    onPickupComplete = { navController.popBackStack() }
                )
            }
            composable(Screen.Delivery.route) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: return@composable
                val deliveryViewModel: DeliveryViewModel = viewModel(
                    key = "delivery-$jobId",
                    factory = viewModelFactory {
                        DeliveryViewModel(
                            jobId = jobId,
                            driverId = driverId,
                            shiftId = shiftId,
                            jobRepository = container.jobRepository,
                            evidenceRepository = container.evidenceRepository,
                            exceptionRepository = container.freightExceptionRepository
                        )
                    }
                )
                DeliveryScreen(
                    deliveryViewModel = deliveryViewModel,
                    evidenceViewModel = evidenceViewModel,
                    jobId = jobId,
                    driverId = driverId,
                    shiftId = shiftId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { evidenceId ->
                        navController.navigate(
                            Screen.CameraCapture.createRoute(evidenceId, STAGE_DELIVERY)
                        )
                    },
                    onNavigateToSignature = { evidenceId ->
                        navController.navigate(
                            Screen.SignatureCapture.createRoute(evidenceId, STAGE_DELIVERY)
                        )
                    },
                    onDeliveryComplete = { navController.popBackStack() }
                )
            }
            composable(Screen.SignatureCapture.route) { entry ->
                val evidenceId = entry.arguments?.getString("evidenceId") ?: return@composable
                val stage = entry.arguments?.getString("stage") ?: STAGE_DELIVERY
                SignatureCaptureScreen(
                    evidenceId = evidenceId,
                    title = if (stage == STAGE_PICKUP) "Sender Signature" else "Recipient Signature",
                    requireSignerName = stage == STAGE_DELIVERY,
                    evidenceViewModel = evidenceViewModel,
                    onCancelled = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.CameraCapture.route) { entry ->
                val evidenceId = entry.arguments?.getString("evidenceId") ?: return@composable
                val stage = entry.arguments?.getString("stage") ?: STAGE_PICKUP
                CameraCaptureScreen(
                    evidenceId = evidenceId,
                    title = if (stage == STAGE_PICKUP) "Pickup Photo" else "Delivery Photo",
                    evidenceViewModel = evidenceViewModel,
                    onCancelled = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.Map.route) { MapScreen(viewModel) }
            composable(Screen.Messages.route) { MessagesScreen(viewModel) }
            composable(Screen.More.route) {
                MoreScreen(
                    viewModel = viewModel,
                    shiftViewModel = shiftViewModel,
                    appVersion = BuildConfig.VERSION_NAME,
                    onLogout = { viewModel.logout() }
                )
            }
        }
    }
}

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)
