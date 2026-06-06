package com.firmmy.dashcam

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun RemoteQrScanner(
    modifier: Modifier = Modifier,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                bindQrScanner(
                    context = context,
                    previewView = this,
                    executor = executor,
                    onPayloadScanned = onPayloadScanned,
                )
            }
        },
    )
}

private fun bindQrScanner(
    context: Context,
    previewView: PreviewView,
    executor: java.util.concurrent.Executor,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
) {
    val lifecycleOwner = context as? LifecycleOwner ?: return
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val scanner = BarcodeScanning.getClient()
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        scanImageProxy(
                            scanner = scanner,
                            imageProxy = imageProxy,
                            onPayloadScanned = onPayloadScanned,
                        )
                    }
                }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        },
        ContextCompat.getMainExecutor(context),
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun scanImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.asSequence()
                .mapNotNull { it.rawValue }
                .mapNotNull(RemoteConnectionPayload::parse)
                .firstOrNull()
                ?.let(onPayloadScanned)
        }
        .addOnCompleteListener { imageProxy.close() }
}
