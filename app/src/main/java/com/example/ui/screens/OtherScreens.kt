package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.ShiftPhase
import com.example.ui.components.PrimaryButton
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.ShiftViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Navigation") }) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("No new messages from dispatch.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: AppViewModel,
    shiftViewModel: ShiftViewModel,
    onLogout: () -> Unit
) {
    val shiftState by shiftViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val shift = shiftState.currentShift
    val isActive = shift?.phase == ShiftPhase.ON_DUTY || shift?.phase == ShiftPhase.ON_BREAK
    var endOdometer by remember(shift?.id) { mutableStateOf("") }
    var endMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Driver Profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Settings")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sync Status: local-first queue active")
                }
            }

            if (isActive && shift != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("End Shift", style = MaterialTheme.typography.titleMedium)
                        Text("Vehicle: ${shift.vehicleId}")
                        Text("Start odometer: ${shift.startOdometer}")
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

            (endMessage ?: shiftState.errorMessage)?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Shift ended")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Log Out",
                onClick = onLogout,
                isDestructive = true
            )
        }
    }
}
