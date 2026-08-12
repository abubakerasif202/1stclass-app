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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.ShiftPhase
import com.example.model.ShiftStatus
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.ShiftViewModel
import kotlinx.coroutines.launch

private const val NOT_PROVIDED = "Not provided"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Navigation") }) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("Map View (Google Maps SDK placeholder)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Messages") }) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("No new messages from dispatch.")
        }
    }
}

/**
 * Driver profile and account actions.
 *
 * Fields the app genuinely does not hold — a phone number the TMS has not supplied, for example —
 * are shown as "Not provided" rather than filled with invented detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: AppViewModel,
    shiftViewModel: ShiftViewModel,
    appVersion: String,
    onLogout: () -> Unit
) {
    val appState by viewModel.uiState.collectAsState()
    val shiftState by shiftViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val driver = appState.driver
    // Only a running shift can be ended; null covers both "no shift" and "not started yet".
    val activeShift = shiftState.currentShift?.takeIf {
        it.phase == ShiftPhase.ON_DUTY || it.phase == ShiftPhase.ON_BREAK
    }
    var endOdometer by remember(activeShift?.id) { mutableStateOf("") }
    var endMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = driver?.name?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = driver?.email?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    ProfileRow("Driver ID", driver?.id?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED)
                    ProfileRow("Phone", driver?.phone?.takeIf { it.isNotBlank() } ?: NOT_PROVIDED)
                    ProfileRow("Current vehicle", driver?.currentVehicleId ?: NOT_PROVIDED)
                    ProfileRow(
                        label = "Shift status",
                        value = when (driver?.shiftStatus) {
                            ShiftStatus.ON_DUTY -> "On duty"
                            ShiftStatus.ON_BREAK -> "On break"
                            else -> "Off duty"
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("Sync")
                    val pending = appState.pendingSyncCount
                    Text(
                        text = if (pending == 0) {
                            "All work is saved on this device. Nothing is waiting to sync."
                        } else {
                            "$pending change${if (pending == 1) "" else "s"} saved on device and " +
                                "waiting to sync."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileRow("App version", appVersion.ifBlank { NOT_PROVIDED })
                }
            }

            if (activeShift != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader("End shift")
                        Text("Vehicle: ${activeShift.vehicleId}")
                        Text("Start odometer: ${activeShift.startOdometer}")
                        OutlinedTextField(
                            value = endOdometer,
                            onValueChange = { endOdometer = it.filter(Char::isDigit) },
                            label = { Text("End odometer *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryButton(
                            text = if (shiftState.isLoading) "Ending Shift…" else "End Shift",
                            onClick = {
                                scope.launch {
                                    shiftViewModel.endShift(endOdometer)
                                        .onSuccess { endMessage = "Shift ended and saved locally." }
                                        .onFailure { endMessage = it.message }
                                }
                            },
                            enabled = endOdometer.isNotBlank() && !shiftState.isLoading,
                            isDestructive = true
                        )
                    }
                }
            }

            (endMessage ?: shiftState.errorMessage ?: appState.error)?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Shift ended")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Signing out keeps completed jobs, inspections and anything waiting to " +
                    "sync on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            PrimaryButton(text = "Log Out", onClick = onLogout, isDestructive = true)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
