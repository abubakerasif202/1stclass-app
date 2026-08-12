package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.JobStatus
import com.example.ui.components.OutlinedActionButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupScreen(
    viewModel: AppViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSignature: () -> Unit,
    onPickupComplete: () -> Unit
) {
    val job = viewModel.getJobById(jobId)
    var itemsCollected by remember { mutableStateOf(job?.itemCount?.toString() ?: "") }
    var notes by remember { mutableStateOf("") }
    var hasPhoto by remember { mutableStateOf(false) }
    var hasSignature by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proof of Pickup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Freight Verification")
                OutlinedTextField(
                    value = itemsCollected,
                    onValueChange = { itemsCollected = it },
                    label = { Text("Items / Pallets Collected *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                SectionHeader("Evidence")
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedActionButton(
                                text = if (hasPhoto) "Photo Taken ✓" else "Take Photo",
                                onClick = { 
                                    hasPhoto = true 
                                    onNavigateToCamera()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedActionButton(
                                text = if (hasSignature) "Signed ✓" else "Get Signature",
                                onClick = { 
                                    hasSignature = true 
                                    onNavigateToSignature()
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
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Complete Pickup",
                    onClick = {
                        viewModel.updateJobStatus(jobId, JobStatus.PICKED_UP)
                        onPickupComplete()
                    },
                    enabled = itemsCollected.isNotBlank() && (hasPhoto || hasSignature)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(
    viewModel: AppViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSignature: () -> Unit,
    onDeliveryComplete: () -> Unit
) {
    val job = viewModel.getJobById(jobId)
    var itemsDelivered by remember { mutableStateOf(job?.itemCount?.toString() ?: "") }
    var notes by remember { mutableStateOf("") }
    var hasPhoto by remember { mutableStateOf(false) }
    var hasSignature by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proof of Delivery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Delivery Verification")
                OutlinedTextField(
                    value = itemsDelivered,
                    onValueChange = { itemsDelivered = it },
                    label = { Text("Items / Pallets Delivered *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                SectionHeader("Evidence")
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedActionButton(
                                text = if (hasPhoto) "Photo Taken ✓" else "Take Photo *",
                                onClick = { 
                                    hasPhoto = true 
                                    onNavigateToCamera()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedActionButton(
                                text = if (hasSignature) "Signed ✓" else "Get Signature *",
                                onClick = { 
                                    hasSignature = true 
                                    onNavigateToSignature()
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
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Complete Delivery",
                    onClick = {
                        viewModel.updateJobStatus(jobId, JobStatus.COMPLETED)
                        onDeliveryComplete()
                    },
                    // Strict requirements for PoD
                    enabled = itemsDelivered.isNotBlank() && hasPhoto && hasSignature
                )
            }
        }
    }
}
