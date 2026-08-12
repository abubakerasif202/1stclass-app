package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.JobStatus
import com.example.model.Location
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    viewModel: AppViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPickup: () -> Unit,
    onNavigateToDelivery: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val job = viewModel.getJobById(jobId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.reference ?: "Job Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (job != null) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    PaddingValues(16.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        when (job.status) {
                            JobStatus.UNASSIGNED, JobStatus.ASSIGNED -> {
                                PrimaryButton(
                                    text = "Start Job",
                                    onClick = { viewModel.updateJobStatus(job.id, JobStatus.IN_PROGRESS) }
                                )
                            }
                            JobStatus.IN_PROGRESS -> {
                                PrimaryButton(
                                    text = "Arrived at Pickup",
                                    onClick = { viewModel.updateJobStatus(job.id, JobStatus.AT_PICKUP) }
                                )
                            }
                            JobStatus.AT_PICKUP -> {
                                PrimaryButton(
                                    text = "Begin Pickup Workflow",
                                    onClick = onNavigateToPickup
                                )
                            }
                            JobStatus.PICKED_UP -> {
                                PrimaryButton(
                                    text = "Depart & Navigate to Delivery",
                                    onClick = { 
                                        viewModel.updateJobStatus(job.id, JobStatus.EN_ROUTE_DELIVERY)
                                        onNavigateToMap()
                                    }
                                )
                            }
                            JobStatus.EN_ROUTE_DELIVERY -> {
                                PrimaryButton(
                                    text = "Arrived at Delivery",
                                    onClick = { viewModel.updateJobStatus(job.id, JobStatus.AT_DELIVERY) }
                                )
                            }
                            JobStatus.AT_DELIVERY -> {
                                PrimaryButton(
                                    text = "Begin Delivery Workflow",
                                    onClick = onNavigateToDelivery
                                )
                            }
                            JobStatus.COMPLETED -> {
                                PrimaryButton(
                                    text = "Job Completed",
                                    onClick = { },
                                    enabled = false
                                )
                            }
                            JobStatus.ISSUE -> {
                                PrimaryButton(
                                    text = "Contact Dispatch",
                                    onClick = { },
                                    isDestructive = true
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (job == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Job not found")
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Status: ${job.status.name.replace("_", " ")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${job.itemCount} Items - ${job.freightDescription}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (job.isDangerousGoods) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ DANGEROUS GOODS",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (job.specialInstructions.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Instructions: ${job.specialInstructions}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("Pickup Details")
                LocationCard(location = job.pickup, windowStart = job.pickupWindowStart, windowEnd = job.pickupWindowEnd)
            }

            item {
                SectionHeader("Delivery Details")
                LocationCard(location = job.delivery, windowStart = job.deliveryWindowStart, windowEnd = job.deliveryWindowEnd)
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LocationCard(location: Location, windowStart: String, windowEnd: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = location.companyName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = location.suburb,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = "Scheduled Window",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$windowStart - $windowEnd",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { /* Launch map */ },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Map", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { /* Launch dialer */ },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Contact: ${location.contactName}",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (location.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes: ${location.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}
