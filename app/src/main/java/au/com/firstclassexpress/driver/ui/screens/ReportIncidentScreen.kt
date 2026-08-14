package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.firstclassexpress.driver.domain.model.IncidentCategory
import au.com.firstclassexpress.driver.domain.model.IncidentSeverity
import au.com.firstclassexpress.driver.domain.model.LocationTrackingState
import au.com.firstclassexpress.driver.navigation.JobNavigation
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.ui.components.SectionHeader
import au.com.firstclassexpress.driver.viewmodel.IncidentViewModel
import kotlinx.coroutines.launch

private const val DISPATCH_PHONE = "1300 178 252" // 1300 1ST CLS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIncidentScreen(
    viewModel: IncidentViewModel,
    locationState: LocationTrackingState,
    jobReference: String?,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Operational Issue") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Urgent Dispatch Contact Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "URGENT DISPATCH CONTACT",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                onClick = { JobNavigation.dialPhone(context, DISPATCH_PHONE) }
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Call Dispatch",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "For vehicle breakdowns, collisions, or safety emergencies, call Operations immediately: $DISPATCH_PHONE",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!jobReference.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Associated Job: ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = jobReference,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Category Selection
            item {
                SectionHeader("1. Issue Category")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        IncidentCategory.entries.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = uiState.selectedCategory == category,
                                        onClick = { viewModel.onCategoryChange(category) }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.selectedCategory == category,
                                    onClick = { viewModel.onCategoryChange(category) }
                                )
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (uiState.selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Severity Level
            item {
                SectionHeader("2. Severity Level")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IncidentSeverity.entries.forEach { severity ->
                        FilterChip(
                            selected = uiState.selectedSeverity == severity,
                            onClick = { viewModel.onSeverityChange(severity) },
                            label = { Text(severity.name) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (severity) {
                                    IncidentSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                                    IncidentSeverity.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Description
            item {
                SectionHeader("3. Description & Notes")
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Describe the situation, site, or freight problem *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    minLines = 3,
                    maxLines = 6
                )
            }

            // Photo Evidence
            item {
                SectionHeader("4. Photo Attachment")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.photoUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Photo attached",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedActionButton(
                                    text = "Retake",
                                    onClick = onNavigateToCamera,
                                    modifier = Modifier.weight(0.4f)
                                )
                            }
                        } else {
                            Text(
                                text = if (uiState.selectedCategory.requiresPhoto) {
                                    "⚠ Photographic evidence is required for ${uiState.selectedCategory.label}."
                                } else {
                                    "Attach an optional photo of the scene, vehicle, or freight."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.selectedCategory.requiresPhoto) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedActionButton(
                                text = "Take Photo",
                                onClick = onNavigateToCamera
                            )
                        }
                    }
                }
            }

            // Error & Submission
            item {
                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                PrimaryButton(
                    text = if (uiState.isSubmitting) "Submitting Report…" else "Submit Issue to Operations",
                    onClick = {
                        val pt = locationState.lastPoint
                        scope.launch {
                            viewModel.submitIncident(
                                latitude = pt?.latitude,
                                longitude = pt?.longitude
                            )
                        }
                    },
                    enabled = uiState.canSubmit
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
