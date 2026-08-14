package au.com.firstclassexpress.driver.ui.capture

import android.Manifest
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import au.com.firstclassexpress.driver.domain.evidence.PendingCapture
import au.com.firstclassexpress.driver.ui.components.OutlinedActionButton
import au.com.firstclassexpress.driver.ui.components.PrimaryButton
import au.com.firstclassexpress.driver.viewmodel.EvidenceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private sealed interface CaptureStage {
    data object Preparing : CaptureStage
    data object Framing : CaptureStage
    data object Capturing : CaptureStage
    data class Review(val stagingPath: String) : CaptureStage
    data class Unavailable(val message: String) : CaptureStage
}

/**
 * Real CameraX capture for job evidence.
 *
 * The photo is staged to disk, reviewed by the driver, and only becomes evidence when they accept
 * it. Cancelling, backing out or retaking removes the staged file and the pending evidence row, so
 * an abandoned camera screen never leaves proof behind.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureScreen(
    evidenceId: String,
    title: String,
    evidenceViewModel: EvidenceViewModel,
    onCancelled: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    var stage by remember { mutableStateOf<CaptureStage>(CaptureStage.Preparing) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(evidenceId) {
        evidenceViewModel.resumeCapture(evidenceId)
            .onSuccess {
                pending = it
                stage = CaptureStage.Framing
            }
            .onFailure {
                stage = CaptureStage.Unavailable(
                    it.message ?: "This capture is no longer available."
                )
            }
    }

    fun cancel() {
        scope.launch {
            (stage as? CaptureStage.Review)?.let { File(it.stagingPath).delete() }
            pending?.let { evidenceViewModel.cancelCapture(it) }
            onCancelled()
        }
    }

    BackHandler { cancel() }

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
            val currentStage = stage
            when {
                currentStage is CaptureStage.Unavailable -> CaptureMessage(
                    title = "Capture unavailable",
                    message = currentStage.message,
                    actionText = "Back",
                    onAction = onCancelled
                )

                !cameraPermission.status.isGranted -> CaptureMessage(
                    title = "Camera access needed",
                    message = if (cameraPermission.status.shouldShowRationale) {
                        "Proof of pickup and delivery is photographic. Without camera access this " +
                            "job cannot be completed on device."
                    } else {
                        "Allow camera access to photograph freight, delivery locations and defects."
                    },
                    actionText = "Allow camera access",
                    onAction = { cameraPermission.launchPermissionRequest() }
                )

                currentStage is CaptureStage.Review -> ReviewCapture(
                    stagingPath = currentStage.stagingPath,
                    isBusy = false,
                    errorMessage = errorMessage,
                    onRetake = {
                        File(currentStage.stagingPath).delete()
                        errorMessage = null
                        stage = CaptureStage.Framing
                    },
                    onUse = {
                        val capture = pending ?: return@ReviewCapture
                        scope.launch {
                            evidenceViewModel.completePhoto(capture, currentStage.stagingPath)
                                .onSuccess { onSaved() }
                                .onFailure {
                                    errorMessage = it.message ?: "Photo could not be saved."
                                }
                        }
                    }
                )

                else -> CameraViewfinder(
                    imageCapture = imageCapture,
                    lifecycleOwner = lifecycleOwner,
                    isCapturing = currentStage is CaptureStage.Capturing,
                    errorMessage = errorMessage,
                    onBindFailed = { stage = CaptureStage.Unavailable(it) },
                    onShutter = {
                        val capture = pending ?: return@CameraViewfinder
                        val stagingPath = evidenceViewModel.stagingPathFor(capture)
                        errorMessage = null
                        stage = CaptureStage.Capturing
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions
                                .Builder(File(stagingPath))
                                .build(),
                            context.mainExecutor(),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    output: ImageCapture.OutputFileResults
                                ) {
                                    stage = CaptureStage.Review(stagingPath)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    File(stagingPath).delete()
                                    errorMessage =
                                        exception.message ?: "The photo could not be taken."
                                    stage = CaptureStage.Framing
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptureMessage(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(text = actionText, onClick = onAction)
    }
}

@Composable
private fun CameraViewfinder(
    imageCapture: ImageCapture,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isCapturing: Boolean,
    errorMessage: String?,
    onBindFailed: (String) -> Unit,
    onShutter: () -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(previewView) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    LaunchedEffect(previewView) {
        runCatching {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }.onFailure {
            onBindFailed(it.message ?: "No camera is available on this device.")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )

        errorMessage?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                FilledIconButton(
                    onClick = onShutter,
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Take photo",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCapture(
    stagingPath: String,
    isBusy: Boolean,
    errorMessage: String?,
    onRetake: () -> Unit,
    onUse: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(stagingPath),
            contentDescription = "Captured photo preview",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        )
        Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = "This photo is not saved yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedActionButton(
                        text = "Retake",
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy
                    )
                    PrimaryButton(
                        text = "Use photo",
                        onClick = onUse,
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy
                    )
                }
            }
        }
    }
}

private fun Context.mainExecutor(): Executor = ContextCompat.getMainExecutor(this)

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) }
                )
            },
            ContextCompat.getMainExecutor(this)
        )
    }
