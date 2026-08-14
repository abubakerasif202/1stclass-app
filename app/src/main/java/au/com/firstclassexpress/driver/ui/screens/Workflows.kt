package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.firstclassexpress.driver.domain.model.EvidenceCaptureRequest
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import au.com.firstclassexpress.driver.domain.model.FreightExceptionRecord
import au.com.firstclassexpress.driver.ui.components.EvidenceGallery
import au.com.firstclassexpress.driver.ui.components.FreightExceptionDialog
import au.com.firstclassexpress.driver.ui.components.FreightExceptionSummary
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.ui.components.SectionHeader
import au.com.firstclassexpress.driver.viewmodel.DeliveryViewModel
import au.com.firstclassexpress.driver.viewmodel.EvidenceViewModel
import au.com.firstclassexpress.driver.viewmodel.PickupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                title = { Text("Confirm Pickup — Step 1 of 2") },
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
                    title = "Confirm Freight & Quantities",
                    body = state.job?.let { "${it.itemCount} × ${it.freightDescription}" }
                        ?: "Loading job details…"
                )
            }

            item {
                SectionHeader("2. Quantity Collected")
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
                SectionHeader("3. Photographic & Signature Evidence")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "A photo of the freight at pickup is mandatory. Additional evidence like sender signature or consignment paperwork can also be captured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedActionButton(
                                text = "Freight Photo",
                                onClick = { capture(EvidenceType.PICKUP_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = "Sender Sig",
                                onClick = { capture(EvidenceType.PICKUP_SIGNATURE, onNavigateToSignature) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedActionButton(
                                text = "Waybill / Doc",
                                onClick = { capture(EvidenceType.CONSIGNMENT_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = "Condition",
                                onClick = { capture(EvidenceType.PICKUP_CONDITION_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        EvidenceGallery(
                            records = state.evidence,
                            onDelete = { record ->
                                scope.launch { evidenceViewModel.deleteEvidence(record.id) }
                            }
                        )
                    }
                }
            }

            item {
                SectionHeader("4. Exceptions & Discrepancies")
                ExceptionsCard(
                    exceptions = state.exceptions,
                    onAdd = { showExceptionDialog = true },
                    onResolve = { id -> scope.launch { pickupViewModel.resolveException(id) } }
                )
            }

            item {
                SectionHeader("5. Pickup Driver Notes")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = pickupViewModel::onNotesChange,
                    label = { Text("Pickup notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 4
                )
            }

            item {
                BlockingReasons(reasons = state.blockingReasons, error = state.errorMessage)
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (state.isSubmitting) "Confirming Pickup…" else "CONFIRM PICKUP & DEPART",
                    onClick = { scope.launch { pickupViewModel.confirmPickup() } },
                    enabled = state.canConfirm
                )
                Spacer(modifier = Modifier.height(32.dp))
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
    var showPodConfirmationDialog by remember { mutableStateOf(false) }

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

    // Professional Final POD Confirmation Dialog
    if (showPodConfirmationDialog) {
        val timeFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
        val currentTimeStr = remember { timeFormatter.format(Date()) }
        val photoCount = state.evidence.count { it.type == EvidenceType.DELIVERY_PHOTO || it.type == EvidenceType.CONSIGNMENT_PHOTO || it.type == EvidenceType.DAMAGED_FREIGHT_PHOTO }
        val hasSignature = state.evidence.any { it.type == EvidenceType.DELIVERY_SIGNATURE }

        AlertDialog(
            onDismissRequest = { showPodConfirmationDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Confirm Proof of Delivery", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Please verify the delivery details before final completion:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PodSummaryRow("Job Reference", state.job?.reference ?: jobId)
                            PodSummaryRow("Recipient Name", state.recipientName)
                            PodSummaryRow("Items Delivered", "${state.itemsDelivered} items")
                            PodSummaryRow("Recorded Time", currentTimeStr)
                            PodSummaryRow("Delivery Location", "${state.job?.delivery?.companyName ?: ""}, ${state.job?.delivery?.suburb ?: ""}")
                            PodSummaryRow("Photos Attached", "$photoCount attached")
                            PodSummaryRow("Signature", if (hasSignature) "✓ Received" else "Not captured")
                            if (state.notes.isNotBlank()) {
                                PodSummaryRow("Driver Notes", state.notes)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPodConfirmationDialog = false
                        scope.launch { deliveryViewModel.completeDelivery() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("COMPLETE DELIVERY", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPodConfirmationDialog = false }) {
                    Text("Back to Edit")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proof of Delivery (POD)") },
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
                    title = "Confirm Freight Delivered",
                    body = state.job?.let { "${it.itemCount} × ${it.freightDescription}" }
                        ?: "Loading job details…"
                )
            }

            item {
                SectionHeader("2. Quantity Delivered")
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
                SectionHeader("3. Received By")
                OutlinedTextField(
                    value = state.recipientName,
                    onValueChange = deliveryViewModel::onRecipientNameChange,
                    label = { Text("Recipient Name (Person signing or accepting) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SectionHeader("4. Mandatory POD Evidence")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Both a delivery photo (e.g. freight at dock) and a recipient signature are mandatory to finalize proof of delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedActionButton(
                                text = "Delivery Photo",
                                onClick = { capture(EvidenceType.DELIVERY_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = "Recipient Signature",
                                onClick = { capture(EvidenceType.DELIVERY_SIGNATURE, onNavigateToSignature) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedActionButton(
                                text = "Signed POD / Doc",
                                onClick = { capture(EvidenceType.CONSIGNMENT_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedActionButton(
                                text = "Damage / Condition",
                                onClick = { capture(EvidenceType.DAMAGED_FREIGHT_PHOTO, onNavigateToCamera) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        EvidenceGallery(
                            records = state.evidence,
                            onDelete = { record ->
                                scope.launch { evidenceViewModel.deleteEvidence(record.id) }
                            }
                        )
                    }
                }
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
                SectionHeader("6. Delivery Driver Notes")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = deliveryViewModel::onNotesChange,
                    label = { Text("Delivery notes (e.g. Left at dock, Gate code, Receiver instructions)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 4
                )
            }

            item {
                BlockingReasons(reasons = state.blockingReasons, error = state.errorMessage)
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (state.isSubmitting) "Finalizing Delivery…" else "REVIEW & COMPLETE DELIVERY",
                    onClick = {
                        if (state.canComplete) {
                            showPodConfirmationDialog = true
                        } else {
                            scope.launch { deliveryViewModel.completeDelivery() }
                        }
                    },
                    enabled = state.canComplete
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PodSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WorkflowStepCard(step: String, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (exceptions.isEmpty()) {
                Text(
                    text = "No open exceptions recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
            OutlinedActionButton(text = "Record Exception / Discrepancy", onClick = onAdd)
        }
    }
}

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
