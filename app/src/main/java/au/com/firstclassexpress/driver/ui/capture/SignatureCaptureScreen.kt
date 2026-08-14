package au.com.firstclassexpress.driver.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.firstclassexpress.driver.domain.evidence.PendingCapture
import au.com.firstclassexpress.driver.domain.evidence.SignatureDrawing
import au.com.firstclassexpress.driver.domain.evidence.SignaturePoint
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.viewmodel.EvidenceViewModel
import kotlinx.coroutines.launch

/**
 * Finger/stylus signature capture.
 *
 * Strokes are drawn as smoothed curves and rendered to a PNG on save. An empty canvas cannot be
 * saved, and — for delivery — neither can a signature without the receiver's printed name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCaptureScreen(
    evidenceId: String,
    title: String,
    requireSignerName: Boolean,
    evidenceViewModel: EvidenceViewModel,
    onCancelled: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var strokes by remember { mutableStateOf(emptyList<List<Offset>>()) }
    var currentStroke by remember { mutableStateOf(emptyList<Offset>()) }
    var canvasSize by remember { mutableStateOf(0 to 0) }
    var signerName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(evidenceId) {
        evidenceViewModel.resumeCapture(evidenceId)
            .onSuccess { pending = it }
            .onFailure { loadError = it.message ?: "This signature request is no longer available." }
    }

    fun cancel() {
        scope.launch {
            pending?.let { evidenceViewModel.cancelCapture(it) }
            onCancelled()
        }
    }

    BackHandler { cancel() }

    val hasInk = strokes.any { it.size >= 2 } || currentStroke.size >= 2
    val canSave = hasInk && !isSaving && pending != null &&
        (!requireSignerName || signerName.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
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
            loadError?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "Sign in the box below",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> currentStroke = listOf(offset) },
                            onDragEnd = {
                                if (currentStroke.size >= 2) strokes = strokes + listOf(currentStroke)
                                currentStroke = emptyList()
                            },
                            onDragCancel = { currentStroke = emptyList() }
                        ) { change, _ -> currentStroke = currentStroke + change.position }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size.width.toInt() to size.height.toInt()
                    (strokes + listOf(currentStroke)).filter { it.size >= 2 }.forEach { stroke ->
                        val path = Path().apply {
                            moveTo(stroke.first().x, stroke.first().y)
                            for (i in 1 until stroke.size) {
                                val previous = stroke[i - 1]
                                val current = stroke[i]
                                quadraticTo(
                                    previous.x,
                                    previous.y,
                                    (previous.x + current.x) / 2f,
                                    (previous.y + current.y) / 2f
                                )
                            }
                            lineTo(stroke.last().x, stroke.last().y)
                        }
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(
                                width = 6f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (requireSignerName) {
                        OutlinedTextField(
                            value = signerName,
                            onValueChange = {
                                signerName = it
                                errorMessage = null
                            },
                            label = { Text("Received by (print name) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedActionButton(
                            text = "Clear",
                            onClick = {
                                strokes = emptyList()
                                currentStroke = emptyList()
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = hasInk && !isSaving
                        )
                        PrimaryButton(
                            text = if (isSaving) "Saving…" else "Save signature",
                            onClick = {
                                val capture = pending ?: return@PrimaryButton
                                val (width, height) = canvasSize
                                val drawing = SignatureDrawing(
                                    strokes = strokes.map { stroke ->
                                        stroke.map { SignaturePoint(it.x, it.y) }
                                    },
                                    widthPx = width,
                                    heightPx = height
                                )
                                isSaving = true
                                scope.launch {
                                    evidenceViewModel
                                        .completeSignature(
                                            capture,
                                            drawing,
                                            signerName.takeIf { requireSignerName }
                                        )
                                        .onSuccess { onSaved() }
                                        .onFailure {
                                            isSaving = false
                                            errorMessage =
                                                it.message ?: "Signature could not be saved."
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = canSave
                        )
                    }
                }
            }
        }
    }
}
