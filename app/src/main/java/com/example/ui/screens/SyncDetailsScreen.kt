package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncStatus
import com.example.ui.components.OutlinedActionButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.SyncStatusRow
import com.example.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics for drivers and support.
 *
 * Shows what is waiting, what failed and why — using the short error summaries the engine stored.
 * It deliberately shows no payloads, no tokens and no PINs: the point is "which operation and how
 * many attempts", not the customer data inside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailsScreen(
    viewModel: SyncViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync details") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SyncStatusRow(summary = state.summary) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("QUEUE")
                        CountRow("Waiting", state.counts.pending)
                        CountRow("Sending", state.counts.inProgress)
                        CountRow("Failed", state.counts.failed)
                        CountRow("Sent", state.counts.synced)
                    }
                }
            }

            item {
                PrimaryButton(text = "Sync now", onClick = viewModel::syncNow)
            }

            if (state.counts.failed > 0) {
                item {
                    OutlinedActionButton(text = "Retry failed sync", onClick = viewModel::retryFailed)
                }
            }

            state.message?.let { message ->
                item {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.operations.isEmpty()) {
                item {
                    Text(
                        text = "Nothing is waiting to sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            } else {
                item { SectionHeader("OUTSTANDING OPERATIONS") }
                items(state.operations, key = { it.id }) { operation ->
                    OperationCard(operation)
                }
            }
        }
    }
}

@Composable
private fun OperationCard(operation: SyncOperation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${operation.entityType} · ${operation.operationType}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Created ${formatTimestamp(operation.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "State: ${label(operation.status)} · attempts: ${operation.retryCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            operation.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun label(status: SyncStatus): String = when (status) {
    SyncStatus.PENDING -> "Waiting"
    SyncStatus.IN_PROGRESS -> "Sending"
    SyncStatus.SYNCED -> "Sent"
    SyncStatus.FAILED -> "Failed"
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
