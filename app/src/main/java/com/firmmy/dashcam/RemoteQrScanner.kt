package com.firmmy.dashcam

import android.content.Context
import android.util.Size
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val foundPayload = AtomicBoolean(false)
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true,
                    ),
                )
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        scanImageProxy(
                            reader = reader,
                            imageProxy = imageProxy,
                            onPayloadScanned = { payload ->
                                if (foundPayload.compareAndSet(false, true)) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        onPayloadScanned(payload)
                                    }
                                }
                            },
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

private fun scanImageProxy(
    reader: MultiFormatReader,
    imageProxy: ImageProxy,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
) {
    try {
        val rawValue = reader.decodeQrText(imageProxy)
        rawValue?.let(RemoteConnectionPayload::parse)?.let(onPayloadScanned)
    } finally {
        reader.reset()
        imageProxy.close()
    }
}

private fun MultiFormatReader.decodeQrText(imageProxy: ImageProxy): String? {
    val yPlane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = yPlane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        yPlane.rowStride,
        imageProxy.height,
        0,
        0,
        imageProxy.width,
        imageProxy.height,
        false,
    )
    return runCatching {
        decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.recoverCatching { error ->
        if (error is NotFoundException) {
            decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
        } else {
            throw error
        }
    }.getOrNull()
}
