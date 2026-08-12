package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    jobId: String,
    evidenceId: String,
    onCancel: () -> Unit,
    onSignatureSaved: (String) -> Unit
) {
    var lines by remember { mutableStateOf(emptyList<List<Offset>>()) }
    var currentLine by remember { mutableStateOf(emptyList<Offset>()) }
    var signerName by remember { mutableStateOf("") }
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Signature") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        lines = emptyList()
                        currentLine = emptyList()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "Prototype signature capture — Phase 1 validates save/cancel integrity. Production POD file rendering is added in Phase 2.",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> currentLine = listOf(offset) },
                            onDragEnd = {
                                if (currentLine.isNotEmpty()) lines = lines + listOf(currentLine)
                                currentLine = emptyList()
                            }
                        ) { change, _ -> currentLine = currentLine + change.position }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    (lines + listOf(currentLine)).forEach { line ->
                        for (i in 1 until line.size) {
                            drawLine(
                                color = Color.Black,
                                start = line[i - 1],
                                end = line[i],
                                strokeWidth = 8f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = signerName,
                        onValueChange = { signerName = it },
                        label = { Text("Signer Name (Print) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Save Prototype Signature",
                        onClick = { onSignatureSaved("prototype://$jobId/signature/$evidenceId") },
                        enabled = lines.isNotEmpty() && signerName.isNotBlank()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    jobId: String,
    evidenceId: String,
    type: String,
    onCancel: () -> Unit,
    onPhotoSaved: (String) -> Unit
) {
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Photo") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "Prototype capture — no real camera file is created in Phase 1. Save/cancel state is persisted correctly.",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
                Text(
                    text = "Prototype Camera Preview",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Save Prototype Photo",
                onClick = { onPhotoSaved("prototype://$jobId/$type/$evidenceId") },
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
