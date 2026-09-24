package com.kyoko412.vrcxcompanion.pairing

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanner(onCode: (String) -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCode = rememberUpdatedState(onCode)
    val latestError = rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val found = AtomicBoolean(false)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) {
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            val code = decodeQr(image)
                            if (code != null && found.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute { latestCode.value(code) }
                            }
                        } catch (_: Exception) {
                            if (found.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute { latestError.value() }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    provider?.unbindAll()
                    provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    latestError.value()
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier.fillMaxWidth().height(280.dp))
}

private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    val width = image.width
    val height = image.height
    val luma = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            luma[y * width + x] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
        }
    }
    return decodeQrLuma(luma, width, height)
}
