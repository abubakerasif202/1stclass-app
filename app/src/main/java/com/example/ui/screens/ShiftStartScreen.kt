package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.PrimaryButton
import com.example.viewmodel.ShiftViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftStartScreen(
    viewModel: ShiftViewModel,
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInspection: (String) -> Unit
) {
    var vehicleId by remember { mutableStateOf("") }
    var trailerId by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Shift") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Vehicle Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = vehicleId,
                onValueChange = { vehicleId = it.uppercase() },
                label = { Text("Vehicle Registration / ID *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = trailerId,
                onValueChange = { trailerId = it.uppercase() },
                label = { Text("Trailer Registration (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = odometer,
                onValueChange = { odometer = it.filter(Char::isDigit) },
                label = { Text("Odometer Reading *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Your shift will remain OFF DUTY until the pre-start inspection is completed and accepted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            PrimaryButton(
                text = if (submitting) "Saving Pre-Start…" else "Begin Pre-Start",
                onClick = {
                    scope.launch {
                        submitting = true
                        errorMessage = null
                        viewModel.beginPreStart(
                            driverId = driverId,
                            vehicleId = vehicleId,
                            trailerId = trailerId,
                            odometer = odometer
                        ).onSuccess(onNavigateToInspection)
                            .onFailure { errorMessage = it.message ?: "Unable to start pre-start" }
                        submitting = false
                    }
                },
                enabled = !submitting && driverId.isNotBlank() && vehicleId.isNotBlank() && odometer.isNotBlank()
            )
        }
    }
}
