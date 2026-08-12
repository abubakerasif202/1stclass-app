package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionHeader
import com.example.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreStartInspectionScreen(
    viewModel: AppViewModel,
    onComplete: () -> Unit
) {
    // Basic state for the mock UI
    val exteriorItems = listOf("Tyres", "Wheels", "Lights", "Indicators", "Mirrors")
    val safetyItems = listOf("Seatbelt", "Horn", "First aid kit")
    val mechanicalItems = listOf("Brakes", "Steering", "Fluid levels")
    
    // In a real app we'd map these properly to state
    var allChecked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pre-Start Inspection") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionHeader("Exterior")
                }
                items(exteriorItems.size) { idx ->
                    InspectionItem(exteriorItems[idx])
                }
                
                item {
                    SectionHeader("Safety")
                }
                items(safetyItems.size) { idx ->
                    InspectionItem(safetyItems[idx])
                }
                
                item {
                    SectionHeader("Mechanical")
                }
                items(mechanicalItems.size) { idx ->
                    InspectionItem(mechanicalItems[idx])
                }
            }
            
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Checkbox(
                            checked = allChecked,
                            onCheckedChange = { allChecked = it }
                        )
                        Text(
                            text = "I declare this vehicle is safe and roadworthy.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    PrimaryButton(
                        text = "Complete Inspection",
                        onClick = onComplete,
                        enabled = allChecked
                    )
                }
            }
        }
    }
}

@Composable
fun InspectionItem(name: String) {
    var status by remember { mutableStateOf<String?>("PASS") } // Defaulting to PASS for prototype speed
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = status == "PASS",
                    onClick = { status = "PASS" },
                    label = { Text("PASS") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                FilterChip(
                    selected = status == "DEFECT",
                    onClick = { status = "DEFECT" },
                    label = { Text("DEFECT") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}
