package au.com.firstclassexpress.driver.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import au.com.firstclassexpress.driver.domain.model.DriverMessage
import au.com.firstclassexpress.driver.domain.model.GpsStatus
import au.com.firstclassexpress.driver.domain.model.LocationTrackingState
import au.com.firstclassexpress.driver.domain.model.MessageCategory
import au.com.firstclassexpress.driver.domain.model.MessageUrgency
import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.rules.ShiftEndValidator
import au.com.firstclassexpress.driver.model.ShiftStatus
import au.com.firstclassexpress.driver.navigation.JobNavigation
import au.com.firstclassexpress.driver.navigation.PreferredNavApp
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.ui.components.SectionHeader
import au.com.firstclassexpress.driver.viewmodel.AppViewModel
import au.com.firstclassexpress.driver.viewmodel.MessageViewModel
import au.com.firstclassexpress.driver.viewmodel.ShiftViewModel
import au.com.firstclassexpress.driver.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val NOT_PROVIDED = "Not provided"

/**
 * Operations Messaging Center for direct dispatcher communication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessageViewModel,
    onNavigateToJob: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Operations Messages", fontWeight = FontWeight.Bold)
                        Text(
                            "${uiState.unreadCount} unread communications",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { JobNavigation.dialPhone(context, "13000001st") }) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Call Dispatch",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All (${uiState.messages.size})") }
                    )
                }
                items(MessageCategory.entries) { category ->
                    val count = uiState.messages.count { it.category == category }
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text("${category.label} ($count)") }
                    )
                }
            }

            if (uiState.filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No operational messages in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredMessages, key = { it.id }) { message ->
                        MessageItemCard(
                            message = message,
                            onClick = { viewModel.markMessageAsRead(message.id) },
                            onOpenJob = { jobId ->
                                viewModel.markMessageAsRead(message.id)
                                onNavigateToJob(jobId)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MessageItemCard(
    message: DriverMessage,
    onClick: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!message.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (message.urgency == MessageUrgency.CRITICAL || message.urgency == MessageUrgency.HIGH) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            } else if (!message.isRead) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!message.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Surface(
                        color = when (message.category) {
                            MessageCategory.DISPATCH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            MessageCategory.JOB_UPDATE -> Color(0xFF29B6F6).copy(alpha = 0.15f)
                            MessageCategory.URGENT -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            MessageCategory.DRIVER_NOTICE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = message.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (message.category) {
                                MessageCategory.DISPATCH -> MaterialTheme.colorScheme.primary
                                MessageCategory.JOB_UPDATE -> Color(0xFF29B6F6)
                                MessageCategory.URGENT -> MaterialTheme.colorScheme.error
                                MessageCategory.DRIVER_NOTICE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            if (!message.jobId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onOpenJob(message.jobId) },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("View Linked Job", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Driver profile, assigned vehicle, sync diagnostics, preferred navigation, and shift management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: AppViewModel,
    shiftViewModel: ShiftViewModel,
    syncViewModel: SyncViewModel,
    appVersion: String,
    locationState: LocationTrackingState,
    onNavigateToSyncDetails: () -> Unit,
    onNavigateToInspection: () -> Unit,
    onNavigateToReportIncident: () -> Unit,
    onLogout: () -> Unit
) {
    val appState by viewModel.uiState.collectAsState()
    val shiftState by shiftViewModel.uiState.collectAsState()
    val syncState by syncViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val driver = appState.driver
    val activeShift = shiftState.currentShift?.takeIf {
        it.phase == ShiftPhase.ON_DUTY || it.phase == ShiftPhase.ON_BREAK
    }
    var endOdometer by remember(activeShift?.id) { mutableStateOf("") }
    var endMessage by remember { mutableStateOf<String?>(null) }
    var showEndShiftWarningDialog by remember { mutableStateOf(false) }
    var selectedNavApp by remember { mutableStateOf(PreferredNavApp.CHOOSER) }
    val context = LocalContext.current

    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    Scaffold(topBar = { TopAppBar(title = { Text("Driver Operations & Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Driver Profile
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = driver?.name?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = driver?.email?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileRow("Driver ID", driver?.id?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED)
                    ProfileRow("Phone Contact", driver?.phone?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED)
                    ProfileRow("Shift Status", when (driver?.shiftStatus) {
                        ShiftStatus.ON_DUTY -> "On Duty"
                        ShiftStatus.ON_BREAK -> "On Break"
                        else -> "Off Duty"
                    })
                }
            }

            // Assigned Vehicle Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("ASSIGNED VEHICLE & EQUIPMENT")
                    ProfileRow("Vehicle Rego / ID", driver?.currentVehicleId ?: "TRK-01 (Heavy Rigid)")
                    ProfileRow("Vehicle Type", "Heavy Rigid (14 Pallet)")
                    ProfileRow("Trailer Rego", activeShift?.trailerId ?: "TRL-994 (Refrigerated)")
                    ProfileRow("Start Odometer", activeShift?.startOdometer?.let { "$it km" } ?: "142,580 km")

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedActionButton(
                            text = "Pre-Start Check",
                            onClick = onNavigateToInspection,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedActionButton(
                            text = "Report Defect",
                            onClick = onNavigateToReportIncident,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Navigation Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("NAVIGATION APP PREFERENCE")
                    Text(
                        "Select your preferred turn-by-turn navigation application:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PreferredNavApp.entries.forEach { navOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNavApp = navOption }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedNavApp == navOption,
                                onClick = { selectedNavApp = navOption }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = navOption.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // System Connectivity & Health Diagnostics
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("SYSTEM HEALTH & CONNECTIVITY")
                    ProfileRow("GPS Telemetry", locationState.status.label)
                    ProfileRow("Fine Location Permission", if (hasFineLocation) "Granted (Precise)" else "Action Required")
                    ProfileRow("Camera (POD/Evidence)", if (hasCamera) "Granted" else "Action Required")
                    ProfileRow("Pending Sync Queue", "${syncState.summary.outstandingCount} items queued")
                    ProfileRow("App Version", appVersion.ifBlank { NOT_PROVIDED })
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedActionButton(
                            text = "Copy Diagnostics",
                            onClick = {
                                val report = """
                                    [1st Class Express System Diagnostics]
                                    App Version: $appVersion
                                    Driver ID: ${driver?.id ?: "N/A"}
                                    Vehicle: ${driver?.currentVehicleId ?: "None"}
                                    Shift Status: ${driver?.shiftStatus?.name ?: "OFF_DUTY"}
                                    GPS Telemetry: ${locationState.status.label}
                                    Fine Location Permission: $hasFineLocation
                                    Camera Permission: $hasCamera
                                    Pending Sync Queue: ${syncState.summary.outstandingCount}
                                    Generated: ${System.currentTimeMillis()}
                                """.trimIndent()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", au.com.firstclassexpress.driver.util.SafeOpsLogger.redact(report)))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedActionButton(
                            text = "Sync Queue",
                            onClick = onNavigateToSyncDetails,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // End Shift Card
            if (activeShift != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader("END SHIFT")
                        Text("Vehicle: ${activeShift.vehicleId}")
                        Text("Start Odometer: ${activeShift.startOdometer} km")
                        OutlinedTextField(
                            value = endOdometer,
                            onValueChange = { endOdometer = it.filter(Char::isDigit) },
                            label = { Text("End odometer reading (km) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        PrimaryButton(
                            text = if (shiftState.isLoading) "Ending Shift…" else "END SHIFT",
                            onClick = {
                                val validation = ShiftEndValidator.validate(
                                    assignedJobs = appState.jobs,
                                    pendingSyncOperationsCount = syncState.summary.outstandingCount,
                                    pendingLocationPointsCount = locationState.queuedCount
                                )
                                if (!validation.canEndSafely) {
                                    showEndShiftWarningDialog = true
                                } else {
                                    scope.launch {
                                        shiftViewModel.endShift(endOdometer)
                                            .onSuccess { endMessage = "Shift ended and saved locally." }
                                            .onFailure { endMessage = it.message }
                                    }
                                }
                            },
                            enabled = endOdometer.isNotBlank() && !shiftState.isLoading,
                            isDestructive = true
                        )
                    }
                }
            }

            // Pre-Flight End Shift Warning Dialog
            if (showEndShiftWarningDialog) {
                val validation = ShiftEndValidator.validate(
                    assignedJobs = appState.jobs,
                    pendingSyncOperationsCount = syncState.summary.outstandingCount,
                    pendingLocationPointsCount = locationState.queuedCount
                )

                AlertDialog(
                    onDismissRequest = { showEndShiftWarningDialog = false },
                    title = { Text("End Shift Pre-Flight Check", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Outstanding operational items detected before clocking off:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            validation.warnings.forEach { warning ->
                                Text(
                                    "• $warning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                "Are you sure you want to end your shift now?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEndShiftWarningDialog = false
                                scope.launch {
                                    shiftViewModel.endShift(endOdometer)
                                        .onSuccess { endMessage = "Shift ended and saved locally." }
                                        .onFailure { endMessage = it.message }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Confirm End Shift")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndShiftWarningDialog = false }) {
                            Text("Review Jobs")
                        }
                    }
                )
            }

            (endMessage ?: shiftState.errorMessage ?: appState.error)?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Shift ended")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryButton(text = "Sign Out", onClick = onLogout, isDestructive = true)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
