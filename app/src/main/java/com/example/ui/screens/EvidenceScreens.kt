package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    jobId: String,
    onNavigateBack: () -> Unit,
    onSignatureSaved: () -> Unit
) {
    var lines by remember { mutableStateOf(emptyList<List<Offset>>()) }
    var currentLine by remember { mutableStateOf(emptyList<Offset>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Signature") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentLine = listOf(offset)
                            },
                            onDragEnd = {
                                lines = lines + listOf(currentLine)
                                currentLine = emptyList()
                            }
                        ) { change, _ ->
                            currentLine = currentLine + change.position
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    lines.forEach { line ->
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
                    for (i in 1 until currentLine.size) {
                        drawLine(
                            color = Color.Black,
                            start = currentLine[i - 1],
                            end = currentLine[i],
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var signerName by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = signerName,
                        onValueChange = { signerName = it },
                        label = { Text("Signer Name (Print) *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    PrimaryButton(
                        text = "Save Signature",
                        onClick = onSignatureSaved,
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
    type: String,
    onNavigateBack: () -> Unit,
    onPhotoSaved: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Photo") },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mocking camera view for prototype
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f/4f)
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
                    text = "Camera Preview",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            PrimaryButton(
                text = "Capture & Save",
                onClick = onPhotoSaved,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
