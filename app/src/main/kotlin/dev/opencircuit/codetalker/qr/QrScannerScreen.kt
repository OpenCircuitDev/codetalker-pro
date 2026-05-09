package dev.opencircuit.codetalker.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * CCT-31 Phase 5c-polish — CameraX QR scanner.
 *
 * Renders a live camera preview, runs each frame through [QrDecoder], and
 * fires [onScanned] the first time it sees a non-null payload. The caller
 * is responsible for parsing the payload (PairingFlow.savePairing handles
 * JSON validation).
 *
 * Permission handling: requests CAMERA on first composition. If the user
 * denies, the screen falls back to a "Use manual entry instead" hint. The
 * companion's manual-entry path is the canonical fallback for any user
 * who wants to skip camera permission entirely.
 */
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Camera permission needed for QR scan.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCancel) { Text("Use manual entry") }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val decoder = QrDecoder()
                var alreadyFired = false

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy: ImageProxy ->
                        if (alreadyFired) { proxy.close(); return@setAnalyzer }
                        try {
                            val plane = proxy.planes[0]
                            val buffer = plane.buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            val payload = decoder.decodeLuminance(
                                yPlane = bytes,
                                width = proxy.width,
                                height = proxy.height,
                                rotation = proxy.imageInfo.rotationDegrees,
                            )
                            if (payload != null) {
                                alreadyFired = true
                                onScanned(payload)
                            }
                        } finally {
                            proxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, analysis,
                        )
                    } catch (_: Throwable) { /* swallow; user can fall back to manual */ }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) {
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}
