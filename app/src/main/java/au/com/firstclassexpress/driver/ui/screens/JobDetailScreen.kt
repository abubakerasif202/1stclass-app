package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.FreightExceptionRecord
import au.com.firstclassexpress.driver.domain.model.JobTimelineEvent
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Location
import au.com.firstclassexpress.driver.model.Priority
import au.com.firstclassexpress.driver.navigation.JobNavigation
import au.com.firstclassexpress.driver.ui.components.EvidenceGallery
import au.com.firstclassexpress.driver.ui.components.FreightExceptionSummary
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.ui.components.SectionHeader
import au.com.firstclassexpress.driver.viewmodel.JobViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    viewModel: JobViewModel,
    jobId: String,
    evidence: List<EvidenceRecord>,
    exceptions: List<FreightExceptionRecord>,
    timelineEvents: List<JobTimelineEvent> = emptyList(),
    onNavigateBack: () -> Unit,
    onNavigateToPickup: () -> Unit,
    onNavigateToDelivery: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToReportIncident: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(jobId) { viewModel.loadJob(jobId) }
    val job = uiState.job
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(job?.reference ?: "Job Details", fontWeight = FontWeight.Bold)
                        Text(
                            text = job?.status?.displayLabel ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToReportIncident(jobId) }) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Report Issue",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (job != null) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (job.status) {
                            JobStatus.UNASSIGNED -> Text(
                                "This job is not assigned to you and cannot be started.",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            JobStatus.ASSIGNED -> if (JobStatus.IN_PROGRESS in uiState.allowedNextStatuses || JobStatus.ACCEPTED in uiState.allowedNextStatuses) {
                                PrimaryButton("Accept & Start Job", onClick = {
                                    scope.launch { viewModel.requestTransition(JobStatus.IN_PROGRESS) }
                                })
                            }
                            JobStatus.ACCEPTED -> if (JobStatus.IN_PROGRESS in uiState.allowedNextStatuses) {
                                PrimaryButton("En Route to Pickup", onClick = {
                                    scope.launch { viewModel.requestTransition(JobStatus.IN_PROGRESS) }
                                })
                            }
                            JobStatus.IN_PROGRESS -> if (JobStatus.AT_PICKUP in uiState.allowedNextStatuses) {
                                PrimaryButton("Arrived at Pickup", onClick = {
                                    scope.launch { viewModel.requestTransition(JobStatus.AT_PICKUP) }
                                })
                            }
                            JobStatus.AT_PICKUP -> PrimaryButton(
                                text = "Begin Pickup Workflow",
                                onClick = onNavigateToPickup
                            )
                            JobStatus.PICKED_UP -> if (JobStatus.EN_ROUTE_DELIVERY in uiState.allowedNextStatuses) {
                                PrimaryButton("Depart & Navigate to Delivery", onClick = {
                                    scope.launch {
                                        viewModel.requestTransition(JobStatus.EN_ROUTE_DELIVERY)
                                            .onSuccess { onNavigateToMap() }
                                    }
                                })
                            }
                            JobStatus.EN_ROUTE_DELIVERY -> if (JobStatus.AT_DELIVERY in uiState.allowedNextStatuses) {
                                PrimaryButton("Arrived at Delivery", onClick = {
                                    scope.launch { viewModel.requestTransition(JobStatus.AT_DELIVERY) }
                                })
                            }
                            JobStatus.AT_DELIVERY -> PrimaryButton(
                                text = "Begin Delivery Workflow (POD)",
                                onClick = onNavigateToDelivery
                            )
                            JobStatus.DELIVERED -> PrimaryButton(
                                text = "Finalize Delivery",
                                onClick = onNavigateToDelivery
                            )
                            JobStatus.COMPLETED -> Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                color = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "DELIVERY COMPLETED",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            JobStatus.DELAYED, JobStatus.FAILED_DELIVERY, JobStatus.CUSTOMER_UNAVAILABLE,
                            JobStatus.VEHICLE_ISSUE, JobStatus.OTHER_EXCEPTION, JobStatus.ISSUE -> {
                                PrimaryButton(
                                    text = "Report / Update Issue Status",
                                    onClick = { onNavigateToReportIncident(jobId) },
                                    isDestructive = true
                                )
                            }
                        }
                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (job == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (uiState.isLoading) "Loading job…" else uiState.errorMessage ?: "Job not found")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dispatch Revision Update Banner
            if (job.revision > 1 || job.specialInstructions.contains("[Updated by Dispatch]")) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Dispatch Revision (Rev v${job.revision})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "This job was updated by Operations. Local evidence has been preserved.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Freight & Consignment Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FREIGHT SPECIFICATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (job.priority == Priority.URGENT) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "URGENT",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${job.itemCount} × ${job.freightDescription}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (job.isDangerousGoods) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "⚠ DANGEROUS GOODS DECLARATION ATTACHED",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (!job.temperatureRequired.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "❄ Required Temp: ${job.temperatureRequired}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (job.specialInstructions.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Instructions: ${job.specialInstructions}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Pickup Card
            item {
                SectionHeader("1. Pickup Location")
                LocationCard(
                    location = job.pickup,
                    windowStart = job.pickupWindowStart,
                    windowEnd = job.pickupWindowEnd,
                    navigationLabel = "NAVIGATE TO PICKUP",
                    onNavigate = { JobNavigation.launchChooser(context, job.pickup) },
                    onCall = { JobNavigation.dialPhone(context, job.pickup.contactPhone) }
                )
            }

            // Delivery Card
            item {
                SectionHeader("2. Delivery Destination")
                LocationCard(
                    location = job.delivery,
                    windowStart = job.deliveryWindowStart,
                    windowEnd = job.deliveryWindowEnd,
                    navigationLabel = "NAVIGATE TO DELIVERY",
                    onNavigate = { JobNavigation.launchChooser(context, job.delivery) },
                    onCall = { JobNavigation.dialPhone(context, job.delivery.contactPhone) }
                )
            }

            // Chronological Job Timeline
            item {
                SectionHeader("Job Timeline & History")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (timelineEvents.isEmpty()) {
                            Text(
                                text = "Job initialized. Events will record as actions occur.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            timelineEvents.forEachIndexed { index, event ->
                                TimelineItemRow(
                                    event = event,
                                    isLast = index == timelineEvents.lastIndex
                                )
                            }
                        }
                    }
                }
            }

            // Evidence & POD Gallery
            item {
                SectionHeader("Captured Evidence & POD")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EvidenceGallery(
                            records = evidence,
                            emptyMessage = "No photos or signatures captured for this job yet."
                        )
                    }
                }
            }

            // Exceptions Section
            if (exceptions.isNotEmpty()) {
                item {
                    SectionHeader("Freight Exceptions")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            exceptions.forEach { record ->
                                FreightExceptionSummary(
                                    reasonLabel = record.reason.label,
                                    notes = record.notes,
                                    resolved = record.resolved,
                                    onResolve = null
                                )
                            }
                        }
                    }
                }
            }

            // Report Incident Action
            item {
                OutlinedActionButton(
                    text = "Report Issue for this Job",
                    onClick = { onNavigateToReportIncident(jobId) }
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun LocationCard(
    location: Location,
    windowStart: String,
    windowEnd: String,
    navigationLabel: String,
    onNavigate: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(location.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(location.address, style = MaterialTheme.typography.bodyMedium)
                    Text(location.suburb, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onNavigate,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = onCall,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Scheduled Window", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        "$windowStart - $windowEnd",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Contact Person", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        location.contactName.ifBlank { "Receiving / Dispatch" },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (location.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Notes: ${location.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = navigationLabel, onClick = onNavigate)
        }
    }
}

@Composable
private fun TimelineItemRow(event: JobTimelineEvent, isLast: Boolean) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(event.timestamp) { timeFormatter.format(Date(event.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timestamp
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp)
        )

        // Line and dot
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }

        // Details
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            event.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
