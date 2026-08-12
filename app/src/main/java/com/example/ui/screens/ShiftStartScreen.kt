package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.PrimaryButton
import com.example.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftStartScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToInspection: () -> Unit
) {
    var vehicleId by remember { mutableStateOf("") }
    var trailerId by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Shift") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                onValueChange = { odometer = it },
                label = { Text("Odometer Reading *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "A pre-start inspection is required before commencing work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            
            PrimaryButton(
                text = "Begin Pre-Start",
                onClick = {
                    viewModel.startShift(vehicleId, odometer)
                    onNavigateToInspection()
                },
                enabled = vehicleId.isNotBlank() && odometer.isNotBlank()
            )
        }
    }
}
