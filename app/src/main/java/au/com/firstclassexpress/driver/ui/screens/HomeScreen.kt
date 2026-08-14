package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.firstclassexpress.driver.domain.model.GpsStatus
import au.com.firstclassexpress.driver.domain.model.LocationTrackingState
import au.com.firstclassexpress.driver.domain.sync.SyncStatusSummary
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Priority
import au.com.firstclassexpress.driver.model.ShiftStatus
import au.com.firstclassexpress.driver.navigation.JobNavigation
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.ui.components.SectionHeader
import au.com.firstclassexpress.driver.ui.components.SyncStatusRow
import au.com.firstclassexpress.driver.viewmodel.AppViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    locationState: LocationTrackingState,
    syncSummary: SyncStatusSummary,
    onNavigateToSyncDetails: () -> Unit,
    onNavigateToShiftStart: () -> Unit,
    onNavigateToJobs: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToReportIncident: (String?) -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToInspection: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val driver = uiState.driver
    val context = LocalContext.current

    // Active job is the current in-flight job assigned to this driver
    val activeJob = uiState.jobs.find {
        it.status != JobStatus.COMPLETED &&
            it.status != JobStatus.UNASSIGNED &&
            !it.status.isTerminal
    }

    val totalToday = uiState.jobs.size
    val jobsRemaining = uiState.jobs.count { it.status != JobStatus.COMPLETED && it.status != JobStatus.ISSUE }
    val jobsCompleted = uiState.jobs.count { it.status == JobStatus.COMPLETED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "1ST CLASS EXPRESS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Driver Operations Portal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMessages) {
                        BadgedBox(
                            badge = {
                                // Notification dot if any
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Messages",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Sync Status Banner
            item {
                SyncStatusRow(summary = syncSummary, onClick = onNavigateToSyncDetails)
            }

            // Driver Shift & Status Card
            item {
                DriverShiftCard(
                    driverName = driver?.name ?: "Unknown Driver",
                    driverId = driver?.id ?: "-",
                    shiftStatus = driver?.shiftStatus ?: ShiftStatus.OFF_DUTY,
                    vehicleId = driver?.currentVehicleId,
                    locationStatus = locationState.status,
                    lastPointRecordedAt = locationState.lastPoint?.recordedAt,
                    onShiftClick = if (driver?.shiftStatus != ShiftStatus.ON_DUTY) onNavigateToShiftStart else onNavigateToInspection
                )
            }

            // Today's Work Summary
            item {
                SectionHeader("Today's Overview")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetricCard(
                        title = "TOTAL",
                        value = totalToday.toString(),
                        subtitle = "Assigned",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "REMAINING",
                        value = jobsRemaining.toString(),
                        subtitle = "Pending",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "COMPLETED",
                        value = jobsCompleted.toString(),
                        subtitle = "Delivered",
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Geofence Arrival Suggestion Banner
            if (activeJob != null) {
                val arrivalSuggestion = au.com.firstclassexpress.driver.domain.rules.GeofenceArrivalEngine.checkArrival(
                    job = activeJob,
                    currentPoint = locationState.lastPoint
                )
                if (arrivalSuggestion != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Site Arrival Detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    arrivalSuggestion.promptText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onNavigateToJobDetail(activeJob.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Confirm Arrival & Proceed")
                                }
                            }
                        }
                    }
                }
            }

            // Primary Active Job Section
            item {
                SectionHeader("Current Active Job")
                if (activeJob != null) {
                    ActiveJobCard(
                        job = activeJob,
                        onClick = { onNavigateToJobDetail(activeJob.id) },
                        onPrimaryAction = { onNavigateToJobDetail(activeJob.id) },
                        onNavigateToMap = {
                            val targetLocation = if (activeJob.status.ordinal < JobStatus.PICKED_UP.ordinal) {
                                activeJob.pickup
                            } else {
                                activeJob.delivery
                            }
                            JobNavigation.launchChooser(context, targetLocation)
                        },
                        onCallContact = {
                            val targetLocation = if (activeJob.status.ordinal < JobStatus.PICKED_UP.ordinal) {
                                activeJob.pickup
                            } else {
                                activeJob.delivery
                            }
                            JobNavigation.dialPhone(context, targetLocation.contactPhone)
                        }
                    )
                } else {
                    EmptyActiveJobCard(
                        isOnDuty = driver?.shiftStatus == ShiftStatus.ON_DUTY,
                        onViewJobs = onNavigateToJobs,
                        onStartShift = onNavigateToShiftStart
                    )
                }
            }

            // Quick Operations Actions Grid
            item {
                SectionHeader("Quick Actions")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionButton(
                        icon = Icons.Default.Warning,
                        label = "Report Issue",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToReportIncident(activeJob?.id) }
                    )
                    QuickActionButton(
                        icon = Icons.Default.Checklist,
                        label = "Pre-Start Check",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToInspection
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionButton(
                        icon = Icons.Default.Email,
                        label = "Messages",
                        color = Color(0xFF29B6F6),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMessages
                    )
                    QuickActionButton(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        label = "All Jobs ($totalToday)",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToJobs
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DriverShiftCard(
    driverName: String,
    driverId: String,
    shiftStatus: ShiftStatus,
    vehicleId: String?,
    locationStatus: GpsStatus,
    lastPointRecordedAt: Long?,
    onShiftClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driverName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Driver ID: $driverId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Shift Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (shiftStatus) {
                        ShiftStatus.ON_DUTY -> Color(0xFF2E7D32)
                        ShiftStatus.ON_BREAK -> Color(0xFFE65100)
                        ShiftStatus.OFF_DUTY -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.clickable(onClick = onShiftClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = when (shiftStatus) {
                                ShiftStatus.ON_DUTY -> "ON DUTY"
                                ShiftStatus.ON_BREAK -> "ON BREAK"
                                ShiftStatus.OFF_DUTY -> "OFF DUTY"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ASSIGNED VEHICLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = vehicleId ?: "None assigned",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (vehicleId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "GPS TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (locationStatus) {
                                GpsStatus.ACTIVE -> Icons.Default.GpsFixed
                                GpsStatus.WAITING_FOR_FIX -> Icons.Default.GpsNotFixed
                                else -> Icons.Default.GpsOff
                            },
                            contentDescription = null,
                            tint = when (locationStatus) {
                                GpsStatus.ACTIVE -> Color(0xFF4CAF50)
                                GpsStatus.WAITING_FOR_FIX, GpsStatus.OFFLINE_QUEUED -> Color(0xFFFFA000)
                                GpsStatus.SYNCING -> Color(0xFF29B6F6)
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = locationStatus.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ActiveJobCard(
    job: Job,
    onClick: () -> Unit,
    onPrimaryAction: () -> Unit,
    onNavigateToMap: () -> Unit,
    onCallContact: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Reference & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = job.reference,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (job.priority == Priority.URGENT) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "URGENT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = job.status.displayLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Leg Destination Highlight
            val isPickupPhase = job.status.ordinal <= JobStatus.AT_PICKUP.ordinal
            val nextStopLabel = if (isPickupPhase) "1. PICKUP STOP" else "2. DELIVERY DESTINATION"
            val currentLocation = if (isPickupPhase) job.pickup else job.delivery
            val currentWindow = if (isPickupPhase) "${job.pickupWindowStart} - ${job.pickupWindowEnd}" else "${job.deliveryWindowStart} - ${job.deliveryWindowEnd}"

            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = nextStopLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Window: $currentWindow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentLocation.companyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${currentLocation.address}, ${currentLocation.suburb}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Freight summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${job.itemCount} items · ${job.freightDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (job.isDangerousGoods) {
                    Text(
                        text = "⚠ DANGEROUS GOODS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Workflow Action Button + Quick Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryActionText = when (job.status) {
                    JobStatus.UNASSIGNED -> "VIEW JOB"
                    JobStatus.ASSIGNED -> "START JOB"
                    JobStatus.ACCEPTED -> "EN ROUTE TO PICKUP"
                    JobStatus.IN_PROGRESS -> "ARRIVED AT PICKUP"
                    JobStatus.AT_PICKUP -> "CONFIRM PICKUP"
                    JobStatus.PICKED_UP -> "NAVIGATE TO DELIVERY"
                    JobStatus.EN_ROUTE_DELIVERY -> "ARRIVED AT DELIVERY"
                    JobStatus.AT_DELIVERY -> "COMPLETE POD"
                    JobStatus.DELIVERED -> "FINALIZE DELIVERY"
                    JobStatus.COMPLETED -> "VIEW COMPLETED"
                    else -> "VIEW JOB DETAILS"
                }

                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = primaryActionText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNavigateToMap,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Navigate")
                }

                IconButton(
                    onClick = onCallContact,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Call Contact")
                }
            }
        }
    }
}

@Composable
private fun EmptyActiveJobCard(
    isOnDuty: Boolean,
    onViewJobs: () -> Unit,
    onStartShift: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "No Active Job in Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isOnDuty) "Check your assigned jobs list to start your next delivery." else "Start your shift and complete pre-start inspection to begin work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isOnDuty) {
                PrimaryButton(text = "View Assigned Jobs", onClick = onViewJobs)
            } else {
                PrimaryButton(text = "Start Shift & Pre-Start", onClick = onStartShift)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
