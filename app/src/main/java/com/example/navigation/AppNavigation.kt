package com.example.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.screens.*
import com.example.viewmodel.AppViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: AppViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            // Only show bottom nav if logged in and on one of the main screens
            val isMainScreen = BottomNavScreens.any { it.route == currentDestination?.route }
            
            if (uiState.isLoggedIn && isMainScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    val items = listOf(
                        BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home),
                        BottomNavItem("Jobs", Screen.JobsList.route, Icons.Filled.List),
                        BottomNavItem("Map", Screen.Map.route, Icons.Filled.LocationOn),
                        BottomNavItem("Messages", Screen.Messages.route, Icons.Filled.Email),
                        BottomNavItem("More", Screen.More.route, Icons.Filled.Menu)
                    )
                    
                    items.forEach { item ->
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
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) // Clear backstack
                        }
                    }
                )
            }
            
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToShiftStart = { navController.navigate(Screen.ShiftStart.route) },
                    onNavigateToJobs = { 
                        navController.navigate(Screen.JobsList.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onNavigateToJobDetail = { jobId -> 
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    }
                )
            }
            
            composable(Screen.ShiftStart.route) {
                ShiftStartScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInspection = { navController.navigate(Screen.PreStartInspection.route) }
                )
            }
            
            composable(Screen.PreStartInspection.route) {
                PreStartInspectionScreen(
                    viewModel = viewModel,
                    onComplete = { 
                        navController.popBackStack(Screen.Home.route, false)
                    }
                )
            }
            
            composable(Screen.JobsList.route) {
                JobsListScreen(
                    viewModel = viewModel,
                    onJobClick = { jobId -> 
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    }
                )
            }
            
            composable(Screen.JobDetail.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                JobDetailScreen(
                    viewModel = viewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPickup = { navController.navigate(Screen.Pickup.createRoute(jobId)) },
                    onNavigateToDelivery = { navController.navigate(Screen.Delivery.createRoute(jobId)) },
                    onNavigateToMap = { 
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            composable(Screen.Pickup.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                PickupScreen(
                    viewModel = viewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { navController.navigate(Screen.CameraCapture.createRoute(jobId, "pickup")) },
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.createRoute(jobId)) },
                    onPickupComplete = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Delivery.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                DeliveryScreen(
                    viewModel = viewModel,
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { navController.navigate(Screen.CameraCapture.createRoute(jobId, "delivery")) },
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.createRoute(jobId)) },
                    onDeliveryComplete = { navController.popBackStack() }
                )
            }
            
            composable(Screen.SignatureCapture.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                SignatureScreen(
                    jobId = jobId,
                    onNavigateBack = { navController.popBackStack() },
                    onSignatureSaved = { navController.popBackStack() }
                )
            }
            
            composable(Screen.CameraCapture.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                val type = backStackEntry.arguments?.getString("type") ?: "evidence"
                CameraScreen(
                    jobId = jobId,
                    type = type,
                    onNavigateBack = { navController.popBackStack() },
                    onPhotoSaved = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Map.route) {
                MapScreen(viewModel = viewModel)
            }
            
            composable(Screen.Messages.route) {
                MessagesScreen(viewModel = viewModel)
            }
            
            composable(Screen.More.route) {
                MoreScreen(
                    viewModel = viewModel,
                    onLogout = { 
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                )
            }
        }
    }
}

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)
