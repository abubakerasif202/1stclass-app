package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.firstclassexpress.driver.domain.model.DefectSeverity
import au.com.firstclassexpress.driver.domain.model.InspectionItemRecord
import au.com.firstclassexpress.driver.domain.model.InspectionItemStatus
import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.viewmodel.InspectionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreStartInspectionScreen(
    viewModel: InspectionViewModel,
    onShiftStarted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pre-Start Inspection") })
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.phase == ShiftPhase.PRESTART_REQUIRED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.declarationAccepted,
                                onCheckedChange = { accepted ->
                                    scope.launch { viewModel.setDeclaration(accepted) }
                                }
                            )
                            Text(
                                text = "I declare these inspection responses are accurate.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        PrimaryButton(
                            text = if (uiState.isSaving) "Validating…" else "Complete Inspection",
                            onClick = { scope.launch { viewModel.completeInspection() } },
                            enabled = !uiState.isSaving &&
                                uiState.items.isNotEmpty() &&
                                uiState.unansweredCount == 0 &&
                                uiState.declarationAccepted
                        )
                    }

                    if (uiState.phase == ShiftPhase.READY_TO_START) {
                        Text(
                            text = "Pre-start passed. The shift is still OFF DUTY until you press Start Shift.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        PrimaryButton(
                            text = "START SHIFT",
                            onClick = {
                                scope.launch {
                                    viewModel.activateShift().onSuccess { onShiftStarted() }
                                }
                            }
                        )
                    }

                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = if (uiState.unansweredCount == 0) {
                        "All mandatory items answered"
                    } else {
                        "${uiState.unansweredCount} mandatory item(s) still unanswered"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.unansweredCount == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val categories = uiState.items.map { it.category }.distinct()
            categories.forEach { category ->
                item(key = "header-$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(
                    items = uiState.items.filter { it.category == category },
                    key = { it.id }
                ) { item ->
                    InspectionItemCard(item = item, viewModel = viewModel)
                }
            }

            uiState.validation?.let { validation ->
                item {
                    when (validation) {
                        ValidationResult.Valid -> Text(
                            text = "Inspection passed.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        is ValidationResult.Invalid -> ValidationMessage(
                            title = "Inspection incomplete",
                            reasons = validation.reasons
                        )
                        is ValidationResult.Blocked -> ValidationMessage(
                            title = "VEHICLE NOT READY — critical defect recorded",
                            reasons = validation.reasons
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InspectionItemCard(
    item: InspectionItemRecord,
    viewModel: InspectionViewModel
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.label, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip(
                    label = "PASS",
                    selected = item.status == InspectionItemStatus.PASS,
                    isError = false,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch { viewModel.setAnswer(item.id, InspectionItemStatus.PASS) }
                }
                StatusChip(
                    label = "DEFECT",
                    selected = item.status == InspectionItemStatus.DEFECT,
                    isError = true,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        viewModel.setAnswer(
                            item.id,
                            InspectionItemStatus.DEFECT,
                            item.defectDescription,
                            item.defectSeverity
                        )
                    }
                }
                StatusChip(
                    label = "N/A",
                    selected = item.status == InspectionItemStatus.NOT_APPLICABLE,
                    isError = false,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch { viewModel.setAnswer(item.id, InspectionItemStatus.NOT_APPLICABLE) }
                }
            }

            if (item.status == InspectionItemStatus.DEFECT) {
                OutlinedTextField(
                    value = item.defectDescription.orEmpty(),
                    onValueChange = { description ->
                        scope.launch {
                            viewModel.setAnswer(
                                item.id,
                                InspectionItemStatus.DEFECT,
                                description,
                                item.defectSeverity
                            )
                        }
                    },
                    label = { Text("Defect description *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text("Severity *", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefectSeverity.entries.forEach { severity ->
                        FilterChip(
                            selected = item.defectSeverity == severity,
                            onClick = {
                                scope.launch {
                                    viewModel.setAnswer(
                                        item.id,
                                        InspectionItemStatus.DEFECT,
                                        item.defectDescription,
                                        severity
                                    )
                                }
                            },
                            label = { Text(severity.name) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (severity == DefectSeverity.CRITICAL) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            },
            selectedLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ValidationMessage(title: String, reasons: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            reasons.forEach { reason ->
                Text("• $reason", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
