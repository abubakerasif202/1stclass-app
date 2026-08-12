package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.JobStatus
import com.example.model.Location
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.JobViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    viewModel: JobViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPickup: () -> Unit,
    onNavigateToDelivery: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(jobId) { viewModel.loadJob(jobId) }
    val job = uiState.job

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.reference ?: "Job Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                "This job is not assigned to this driver and cannot be started.",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            JobStatus.ASSIGNED -> if (JobStatus.IN_PROGRESS in uiState.allowedNextStatuses) {
                                PrimaryButton("Start Job", onClick = {
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
                                text = "Begin Delivery Workflow",
                                onClick = onNavigateToDelivery
                            )
                            JobStatus.COMPLETED -> PrimaryButton(
                                text = "Job Completed",
                                onClick = {},
                                enabled = false
                            )
                            JobStatus.ISSUE -> PrimaryButton(
                                text = "Contact Dispatch",
                                onClick = {},
                                isDestructive = true
                            )
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
                        Text("${job.itemCount} Items - ${job.freightDescription}")
                        if (job.isDangerousGoods) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("⚠ DANGEROUS GOODS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        if (job.specialInstructions.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Instructions: ${job.specialInstructions}")
                        }
                    }
                }
            }
            item {
                SectionHeader("Pickup Details")
                LocationCard(job.pickup, job.pickupWindowStart, job.pickupWindowEnd)
            }
            item {
                SectionHeader("Delivery Details")
                LocationCard(job.delivery, job.deliveryWindowStart, job.deliveryWindowEnd)
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
            Text(location.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(location.address)
            Text(location.suburb)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Scheduled Window", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "$windowStart - $windowEnd",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {},
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) { Icon(Icons.Default.Map, contentDescription = "Map", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(
                        onClick = {},
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) { Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Contact: ${location.contactName} • ${location.contactPhone}", style = MaterialTheme.typography.bodySmall)
            if (location.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Notes: ${location.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
