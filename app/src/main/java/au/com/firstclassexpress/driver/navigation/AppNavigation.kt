package au.com.firstclassexpress.driver.navigation

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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import au.com.firstclassexpress.driver.AppContainer
import au.com.firstclassexpress.driver.BuildConfig
import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.model.GpsStatus
import au.com.firstclassexpress.driver.ui.location.LocationPermissionCoordinator
import au.com.firstclassexpress.driver.ui.location.hasLocationPermission
import au.com.firstclassexpress.driver.ui.capture.CameraCaptureScreen
import au.com.firstclassexpress.driver.ui.capture.SignatureCaptureScreen
import au.com.firstclassexpress.driver.ui.screens.*
import au.com.firstclassexpress.driver.viewmodel.*
import au.com.firstclassexpress.driver.push.PushDestination

private const val STAGE_PICKUP = "pickup"
private const val STAGE_DELIVERY = "delivery"

internal fun initialRoute(isLoggedIn: Boolean): String =
    if (isLoggedIn) Screen.Home.route else Screen.Login.route

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
    container: AppContainer,
    notificationDestination: PushDestination? = null,
    onNotificationHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isRestoringSession) {
        BrandedLoadingScreen()
        return
    }

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
    val syncViewModel: SyncViewModel = viewModel(
        factory = viewModelFactory {
            SyncViewModel(
                syncRepository = container.syncRepository,
                syncQueue = container.syncQueue,
                connectivityRepository = container.connectivityRepository,
                environment = container.tmsEnvironment,
                requestSync = container.syncScheduler::requestImmediateSync,
                isOnlineNow = container.connectivityRepository::isOnline
            )
        }
    )
    val messageViewModel: MessageViewModel = viewModel(
        factory = viewModelFactory {
            MessageViewModel(container.messageRepository)
        }
    )

    val driverId = uiState.driver?.id.orEmpty()
    val shiftId = uiState.currentShiftId
    val onDuty = uiState.currentShift?.phase == ShiftPhase.ON_DUTY ||
        uiState.currentShift?.phase == ShiftPhase.ON_BREAK
    val locationState by container.locationStateStore.state.collectAsState()
    val unreadMessageCount by messageViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun reconcileTracking() {
        container.locationTrackingController.reconcile(onDuty, uiState.isLoggedIn)
        if (!onDuty || !uiState.isLoggedIn) {
            container.locationStateStore.updateStatus(GpsStatus.OFF, false)
        } else if (!context.hasLocationPermission()) {
            container.locationStateStore.updateStatus(GpsStatus.PERMISSION_REQUIRED, true)
        }
    }
    LaunchedEffect(onDuty, uiState.isLoggedIn) { reconcileTracking() }
    DisposableEffect(lifecycleOwner, onDuty, uiState.isLoggedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reconcileTracking()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LocationPermissionCoordinator(onDuty = onDuty, onPermissionChanged = ::reconcileTracking)

    // Sign-out anywhere in the app returns to the login screen.
    LaunchedEffect(uiState.isLoggedIn, uiState.isRestoringSession) {
        if (!uiState.isRestoringSession && !uiState.isLoggedIn) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != null && current != Screen.Login.route) {
                navController.navigate(Screen.Login.route) { popUpTo(0) }
            }
        }
    }

    LaunchedEffect(uiState.isLoggedIn, notificationDestination) {
        val destination = notificationDestination ?: return@LaunchedEffect
        if (!uiState.isLoggedIn) return@LaunchedEffect
        when (destination) {
            is PushDestination.JobDetail -> {
                navController.navigate(Screen.JobDetail.createRoute(destination.jobId)) {
                    launchSingleTop = true
                }
            }
            PushDestination.Messages -> {
                navController.navigate(Screen.Messages.route) { launchSingleTop = true }
            }
        }
        onNotificationHandled()
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
                        BottomNavItem("Messages", Screen.Messages.route, Icons.Filled.Email, unreadMessageCount.unreadCount),
                        BottomNavItem("Profile", Screen.More.route, Icons.Filled.Menu)
                    ).forEach { item ->
                        NavigationBarItem(
                            icon = {
                                if (item.badgeCount > 0) {
                                    BadgedBox(badge = { Badge { Text(item.badgeCount.toString()) } }) {
                                        Icon(item.icon, contentDescription = item.name)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.name)
                                }
                            },
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
            startDestination = initialRoute(uiState.isLoggedIn),
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(viewModel) {
                    navController.navigate(Screen.Home.route) { popUpTo(0) }
                }
            }
            composable(Screen.Home.route) {
                val shiftState by shiftViewModel.uiState.collectAsState()
                val syncState by syncViewModel.uiState.collectAsState()
                HomeScreen(
                    viewModel = viewModel,
                    locationState = locationState,
                    syncSummary = syncState.summary,
                    onNavigateToSyncDetails = {
                        navController.navigate(Screen.SyncDetails.route)
                    },
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
                    },
                    onNavigateToReportIncident = { targetJobId ->
                        navController.navigate(Screen.ReportIncident.createRoute(targetJobId))
                    },
                    onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                    onNavigateToInspection = {
                        val current = shiftState.currentShift
                        if (current != null) {
                            navController.navigate(Screen.PreStartInspection.createRoute(current.id))
                        } else {
                            navController.navigate(Screen.ShiftStart.route)
                        }
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
                val timelineEvents by container.jobTimelineRepository.observeEventsForJob(jobId)
                    .collectAsState(initial = emptyList())
                JobDetailScreen(
                    viewModel = jobViewModel,
                    jobId = jobId,
                    evidence = evidence,
                    exceptions = exceptions,
                    timelineEvents = timelineEvents,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPickup = {
                        navController.navigate(Screen.Pickup.createRoute(jobId))
                    },
                    onNavigateToDelivery = {
                        navController.navigate(Screen.Delivery.createRoute(jobId))
                    },
                    onNavigateToMap = { navController.navigate(Screen.Map.route) },
                    onNavigateToReportIncident = { targetJobId ->
                        navController.navigate(Screen.ReportIncident.createRoute(targetJobId))
                    }
                )
            }
            composable(Screen.ReportIncident.route) { entry ->
                val reportJobId = entry.arguments?.getString("jobId")
                val incidentViewModel: IncidentViewModel = viewModel(
                    key = "incident-${reportJobId.orEmpty()}",
                    factory = viewModelFactory {
                        IncidentViewModel(
                            incidentRepository = container.incidentRepository,
                            driverId = driverId,
                            shiftId = shiftId,
                            initialJobId = reportJobId
                        )
                    }
                )
                ReportIncidentScreen(
                    viewModel = incidentViewModel,
                    locationState = locationState,
                    jobReference = reportJobId?.let { id -> uiState.jobs.find { it.id == id }?.reference },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = {
                        // Launch camera with ad-hoc evidence or staging
                    }
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
            composable(Screen.Map.route) { MapScreen(viewModel, locationState) }
            composable(Screen.Messages.route) {
                MessagesScreen(
                    viewModel = messageViewModel,
                    onNavigateToJob = { targetJobId ->
                        navController.navigate(Screen.JobDetail.createRoute(targetJobId))
                    }
                )
            }
            composable(Screen.More.route) {
                val shiftState by shiftViewModel.uiState.collectAsState()
                MoreScreen(
                    viewModel = viewModel,
                    shiftViewModel = shiftViewModel,
                    syncViewModel = syncViewModel,
                    appVersion = BuildConfig.VERSION_NAME,
                    locationState = locationState,
                    onNavigateToSyncDetails = {
                        navController.navigate(Screen.SyncDetails.route)
                    },
                    onNavigateToInspection = {
                        val current = shiftState.currentShift
                        if (current != null) {
                            navController.navigate(Screen.PreStartInspection.createRoute(current.id))
                        } else {
                            navController.navigate(Screen.ShiftStart.route)
                        }
                    },
                    onNavigateToReportIncident = {
                        navController.navigate(Screen.ReportIncident.createRoute(null))
                    },
                    onLogout = { viewModel.logout() }
                )
            }
            composable(Screen.SyncDetails.route) {
                SyncDetailsScreen(
                    viewModel = syncViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)
