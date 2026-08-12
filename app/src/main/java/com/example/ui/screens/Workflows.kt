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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import com.example.domain.model.FreightExceptionRecord
import com.example.ui.components.EvidenceGallery
import com.example.ui.components.FreightExceptionDialog
import com.example.ui.components.FreightExceptionSummary
import com.example.ui.components.OutlinedActionButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.DeliveryViewModel
import com.example.viewmodel.EvidenceViewModel
import com.example.viewmodel.PickupViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupScreen(
    pickupViewModel: PickupViewModel,
    evidenceViewModel: EvidenceViewModel,
    jobId: String,
    driverId: String,
    shiftId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSignature: (String) -> Unit,
    onPickupComplete: () -> Unit
) {
    val state by pickupViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showExceptionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isComplete) { if (state.isComplete) onPickupComplete() }

    fun capture(type: EvidenceType, onReady: (String) -> Unit) {
        scope.launch {
            evidenceViewModel
                .beginCapture(EvidenceCaptureRequest(jobId, type, driverId, shiftId))
                .onSuccess { onReady(it.evidenceId) }
        }
    }

    if (showExceptionDialog) {
        FreightExceptionDialog(
            hasSavedPhoto = evidenceViewModel.isRequirementSatisfied(
                state.evidence,
                EvidenceType.PICKUP_PHOTO
            ),
            errorMessage = state.errorMessage,
            onDismiss = {
                showExceptionDialog = false
                pickupViewModel.clearError()
            },
            onAddPhoto = {
                showExceptionDialog = false
                capture(EvidenceType.PICKUP_PHOTO, onNavigateToCamera)
            },
            onSave = { reason, notes ->
                scope.launch {
                    pickupViewModel.recordException(reason, notes)
                        .onSuccess { showExceptionDialog = false }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Pickup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                WorkflowStepCard(
                    step = "1",
                    title = "Confirm freight",
                    body = state.job?.let { "${it.itemCount} × ${it.freightDescription}" }
                        ?: "Loading job…"
                )
            }
            item {
                SectionHeader("2. Quantity collected")
                OutlinedTextField(
                    value = state.itemsCollected,
                    onValueChange = pickupViewModel::onItemsCollectedChange,
                    label = { Text("Items / pallets / cartons collected *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader("3. Pickup evidence")
                EvidenceCaptureCard(
                    helper = "At least one photo of the freight at pickup is required. " +
                        "Evidence only counts once it has been saved to this device.",
                    records = state.evidence,
                    primaryLabel = "Photograph freight",
                    secondaryLabel = "Sender signature (optional)",
                    onPrimary = { capture(EvidenceType.PICKUP_PHOTO, onNavigateToCamera) },
                    onSecondary = {
                        capture(EvidenceType.PICKUP_SIGNATURE, onNavigateToSignature)
                    },
                    onDelete = { record ->
                        scope.launch { evidenceViewModel.deleteEvidence(record.id) }
                    }
                )
            }
            item {
                SectionHeader("4. Exceptions")
                ExceptionsCard(
                    exceptions = state.exceptions,
                    onAdd = { showExceptionDialog = true },
                    onResolve = { id -> scope.launch { pickupViewModel.resolveException(id) } }
                )
            }
            item {
                SectionHeader("5. Notes")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = pickupViewModel::onNotesChange,
                    label = { Text("Pickup notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4
                )
            }
            item {
                BlockingReasons(reasons = state.blockingReasons, error = state.errorMessage)
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (state.isSubmitting) "Confirming…" else "Confirm pickup",
                    onClick = { scope.launch { pickupViewModel.confirmPickup() } },
                    enabled = state.canConfirm
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(
    deliveryViewModel: DeliveryViewModel,
    evidenceViewModel: EvidenceViewModel,
    jobId: String,
    driverId: String,
    shiftId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSignature: (String) -> Unit,
    onDeliveryComplete: () -> Unit
) {
    val state by deliveryViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showExceptionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isComplete) { if (state.isComplete) onDeliveryComplete() }

    fun capture(type: EvidenceType, onReady: (String) -> Unit) {
        scope.launch {
            evidenceViewModel
                .beginCapture(EvidenceCaptureRequest(jobId, type, driverId, shiftId))
                .onSuccess { onReady(it.evidenceId) }
        }
    }

    if (showExceptionDialog) {
        FreightExceptionDialog(
            hasSavedPhoto = evidenceViewModel.isRequirementSatisfied(
                state.evidence,
                EvidenceType.DELIVERY_PHOTO
            ),
            errorMessage = state.errorMessage,
            onDismiss = {
                showExceptionDialog = false
                deliveryViewModel.clearError()
            },
            onAddPhoto = {
                showExceptionDialog = false
                capture(EvidenceType.DELIVERY_PHOTO, onNavigateToCamera)
            },
            onSave = { reason, notes ->
                scope.launch {
                    deliveryViewModel.recordException(reason, notes)
                        .onSuccess { showExceptionDialog = false }
                }
            }
        )
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WorkflowStepCard(
                    step = "1",
                    title = "Confirm freight condition",
                    body = state.job?.let { "${it.itemCount} × ${it.freightDescription}" }
                        ?: "Loading job…"
                )
            }
            item {
                SectionHeader("2. Quantity delivered")
                OutlinedTextField(
                    value = state.itemsDelivered,
                    onValueChange = deliveryViewModel::onItemsDeliveredChange,
                    label = { Text("Items / pallets / cartons delivered *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader("3. Received by")
                OutlinedTextField(
                    value = state.recipientName,
                    onValueChange = deliveryViewModel::onRecipientNameChange,
                    label = { Text("Recipient name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader("4. POD evidence")
                EvidenceCaptureCard(
                    helper = "A delivery photo and a recipient signature are both required.",
                    records = state.evidence,
                    primaryLabel = "Photograph delivery",
                    secondaryLabel = "Capture signature",
                    onPrimary = { capture(EvidenceType.DELIVERY_PHOTO, onNavigateToCamera) },
                    onSecondary = {
                        capture(EvidenceType.DELIVERY_SIGNATURE, onNavigateToSignature)
                    },
                    onDelete = { record ->
                        scope.launch { evidenceViewModel.deleteEvidence(record.id) }
                    }
                )
            }
            item {
                SectionHeader("5. Exceptions")
                ExceptionsCard(
                    exceptions = state.exceptions,
                    onAdd = { showExceptionDialog = true },
                    onResolve = { id -> scope.launch { deliveryViewModel.resolveException(id) } }
                )
            }
            item {
                SectionHeader("6. Delivery notes")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = deliveryViewModel::onNotesChange,
                    label = { Text("Delivery notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4
                )
            }
            item {
                BlockingReasons(reasons = state.blockingReasons, error = state.errorMessage)
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (state.isSubmitting) "Completing…" else "Complete delivery",
                    onClick = { scope.launch { deliveryViewModel.completeDelivery() } },
                    enabled = state.canComplete
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WorkflowStepCard(step: String, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "STEP $step",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EvidenceCaptureCard(
    helper: String,
    records: List<EvidenceRecord>,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDelete: (EvidenceRecord) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = helper, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedActionButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedActionButton(
                    text = secondaryLabel,
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            EvidenceGallery(records = records, onDelete = onDelete)
        }
    }
}

@Composable
private fun ExceptionsCard(
    exceptions: List<FreightExceptionRecord>,
    onAdd: () -> Unit,
    onResolve: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (exceptions.isEmpty()) {
                Text(
                    text = "No exceptions recorded.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                exceptions.forEachIndexed { index, record ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                    FreightExceptionSummary(
                        reasonLabel = record.reason.label,
                        notes = record.notes,
                        resolved = record.resolved,
                        onResolve = { onResolve(record.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedActionButton(text = "Record exception", onClick = onAdd)
        }
    }
}

/** Shows exactly why a confirm button is disabled, in the domain's own words. */
@Composable
private fun BlockingReasons(reasons: List<String>, error: String?) {
    if (reasons.isEmpty() && error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        error?.takeIf { it !in reasons }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        reasons.forEach {
            Text(
                text = "• $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
