package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.EvidenceType
import com.example.model.JobStatus
import com.example.ui.components.OutlinedActionButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.EvidenceViewModel
import com.example.viewmodel.JobViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupScreen(
    jobViewModel: JobViewModel,
    evidenceViewModel: EvidenceViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSignature: (String) -> Unit,
    onPickupComplete: () -> Unit
) {
    val jobState by jobViewModel.uiState.collectAsState()
    val evidence by evidenceViewModel.observeForJob(jobId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(jobId) { jobViewModel.loadJob(jobId) }
    val job = jobState.job
    var itemsCollected by remember(job?.itemCount) { mutableStateOf(job?.itemCount?.toString().orEmpty()) }
    var notes by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }

    val hasPhoto = evidenceViewModel.isRequirementSatisfied(evidence, EvidenceType.PICKUP_PHOTO)
    val hasSignature = evidenceViewModel.isRequirementSatisfied(evidence, EvidenceType.PICKUP_SIGNATURE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proof of Pickup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Freight Verification")
                OutlinedTextField(
                    value = itemsCollected,
                    onValueChange = { itemsCollected = it.filter(Char::isDigit) },
                    label = { Text("Items / Pallets Collected *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader("Evidence")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Evidence only counts after an explicit Save. Back/cancel does not count.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedActionButton(
                                text = if (hasPhoto) "Photo Saved ✓" else "Take Photo",
                                onClick = {
                                    scope.launch {
                                        evidenceViewModel.beginCapture(jobId, EvidenceType.PICKUP_PHOTO)
                                            .onSuccess(onNavigateToCamera)
                                            .onFailure { actionError = it.message }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = if (hasSignature) "Signature Saved ✓" else "Get Signature",
                                onClick = {
                                    scope.launch {
                                        evidenceViewModel.beginCapture(jobId, EvidenceType.PICKUP_SIGNATURE)
                                            .onSuccess(onNavigateToSignature)
                                            .onFailure { actionError = it.message }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader("Additional Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Pickup notes or discrepancies") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 4
                )
            }
            actionError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Complete Pickup",
                    onClick = {
                        scope.launch {
                            jobViewModel.requestTransition(JobStatus.PICKED_UP)
                                .onSuccess { onPickupComplete() }
                                .onFailure { actionError = it.message }
                        }
                    },
                    enabled = itemsCollected.isNotBlank() && (hasPhoto || hasSignature) && job?.status == JobStatus.AT_PICKUP
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(
    jobViewModel: JobViewModel,
    evidenceViewModel: EvidenceViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSignature: (String) -> Unit,
    onDeliveryComplete: () -> Unit
) {
    val jobState by jobViewModel.uiState.collectAsState()
    val evidence by evidenceViewModel.observeForJob(jobId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(jobId) { jobViewModel.loadJob(jobId) }
    val job = jobState.job
    var itemsDelivered by remember(job?.itemCount) { mutableStateOf(job?.itemCount?.toString().orEmpty()) }
    var notes by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }

    val hasPhoto = evidenceViewModel.isRequirementSatisfied(evidence, EvidenceType.DELIVERY_PHOTO)
    val hasSignature = evidenceViewModel.isRequirementSatisfied(evidence, EvidenceType.DELIVERY_SIGNATURE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proof of Delivery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Delivery Verification")
                OutlinedTextField(
                    value = itemsDelivered,
                    onValueChange = { itemsDelivered = it.filter(Char::isDigit) },
                    label = { Text("Items / Pallets Delivered *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader("Evidence")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Delivery requires both a saved photo and saved signature.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedActionButton(
                                text = if (hasPhoto) "Photo Saved ✓" else "Take Photo *",
                                onClick = {
                                    scope.launch {
                                        evidenceViewModel.beginCapture(jobId, EvidenceType.DELIVERY_PHOTO)
                                            .onSuccess(onNavigateToCamera)
                                            .onFailure { actionError = it.message }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = if (hasSignature) "Signature Saved ✓" else "Get Signature *",
                                onClick = {
                                    scope.launch {
                                        evidenceViewModel.beginCapture(jobId, EvidenceType.DELIVERY_SIGNATURE)
                                            .onSuccess(onNavigateToSignature)
                                            .onFailure { actionError = it.message }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader("Exceptions & Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Damaged, short delivery, etc.") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 4
                )
            }
            actionError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Complete Delivery",
                    onClick = {
                        scope.launch {
                            jobViewModel.requestTransition(JobStatus.COMPLETED)
                                .onSuccess { onDeliveryComplete() }
                                .onFailure { actionError = it.message }
                        }
                    },
                    enabled = itemsDelivered.isNotBlank() && hasPhoto && hasSignature && job?.status == JobStatus.AT_DELIVERY
                )
            }
        }
    }
}
