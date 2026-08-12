package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Job
import com.example.model.JobStatus
import com.example.model.ShiftStatus
import com.example.ui.components.OutlinedActionButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToShiftStart: () -> Unit,
    onNavigateToJobs: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val driver = uiState.driver
    
    val activeJob = uiState.jobs.find { 
        it.status != JobStatus.COMPLETED && 
        it.status != JobStatus.UNASSIGNED &&
        it.status != JobStatus.ISSUE
    }
    
    val jobsRemaining = uiState.jobs.count { it.status != JobStatus.COMPLETED && it.status != JobStatus.ISSUE }
    val jobsCompleted = uiState.jobs.count { it.status == JobStatus.COMPLETED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Driver Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = driver?.name ?: "Unknown Driver",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ID: ${driver?.id ?: "-"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = when (driver?.shiftStatus) {
                                        ShiftStatus.ON_DUTY -> "ON DUTY"
                                        ShiftStatus.ON_BREAK -> "ON BREAK"
                                        ShiftStatus.OFF_DUTY -> "OFF DUTY"
                                        else -> "UNKNOWN"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (driver?.shiftStatus == ShiftStatus.ON_DUTY) 
                                                MaterialTheme.colorScheme.primary 
                                           else MaterialTheme.colorScheme.error
                                )
                            }
                            
                            if (driver?.shiftStatus == ShiftStatus.ON_DUTY && driver.currentVehicleId != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Vehicle",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = driver.currentVehicleId,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Today's Progress")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProgressCard(
                        modifier = Modifier.weight(1f),
                        title = "Remaining",
                        value = jobsRemaining.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ProgressCard(
                        modifier = Modifier.weight(1f),
                        title = "Completed",
                        value = jobsCompleted.toString(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (driver?.shiftStatus != ShiftStatus.ON_DUTY) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Start Shift",
                        onClick = onNavigateToShiftStart
                    )
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (activeJob != null) {
                        SectionHeader("Current Job")
                        CurrentJobCard(
                            job = activeJob,
                            onClick = { onNavigateToJobDetail(activeJob.id) }
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No Active Job",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Check your jobs list for assigned work.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                PrimaryButton(
                                    text = "View Jobs",
                                    onClick = onNavigateToJobs
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButton(
                        text = "Report Issue",
                        onClick = { /* Navigate to issue report */ },
                        modifier = Modifier.fillMaxWidth()
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
fun ProgressCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun CurrentJobCard(job: Job, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.reference,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = job.status.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val destLabel = if (job.status.ordinal < JobStatus.PICKED_UP.ordinal) "PICKUP" else "DELIVERY"
            val destLocation = if (job.status.ordinal < JobStatus.PICKED_UP.ordinal) job.pickup else job.delivery
            
            Text(
                text = "NEXT STOP: $destLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = destLocation.companyName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = destLocation.suburb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
