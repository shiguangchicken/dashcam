package com.firmmy.dashcam.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.LifecycleOwner
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraXCameraFacade(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val clock: () -> Instant = { Instant.now() },
    private val onPreviewFrame: (ByteArray) -> Unit = {},
    private val previewSurfaceProvider: () -> Preview.SurfaceProvider? = { null },
) : CameraRecordingFacade {
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var activeFile: File? = null
    private var activeStartedAt: Instant? = null
    private var finalizeContinuation: Continuation<DashCamResult<CompletedCameraRecording>>? = null
    private val lastPreviewFrameAt = AtomicLong(0L)
    private val lastPreviewJpeg = AtomicReference<ByteArray?>(null)

    override suspend fun startRecording(
        outputFile: File,
        profile: RecordingProfile,
    ): DashCamResult<ActiveCameraRecording> {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return DashCamResult.Failure(DashCamError.PermissionDenied(Manifest.permission.CAMERA))
        }
        if (
            profile.audioEnabled &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return DashCamResult.Failure(DashCamError.PermissionDenied(Manifest.permission.RECORD_AUDIO))
        }
        if (recording != null) {
            return DashCamResult.Failure(DashCamError.InvalidState("recording", "Recording is already active"))
        }

        return runCatching {
            val provider = obtainCameraProvider()
            val recorder = Recorder.Builder()
                .setQualitySelector(profile.quality.toQualitySelector())
                .setTargetVideoEncodingBitRate(profile.bitrateKbps * 1_000)
                .build()
            val capture = VideoCapture.withOutput(recorder)
            val preview = previewSurfaceProvider()?.let { surfaceProvider ->
                Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(surfaceProvider) }
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(PREVIEW_WIDTH, PREVIEW_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(executor) { imageProxy ->
                        analyzePreviewFrame(imageProxy)
                    }
                }

            provider.unbindAll()
            bindRecordingUseCases(provider, capture, preview, analysis)
            videoCapture = capture
            imageCapture = null

            val pendingRecording = recorder.prepareRecording(context, FileOutputOptions.Builder(outputFile).build())
                .withAudioIfNeeded(profile.audioEnabled)
            val startedAt = clock()
            activeFile = outputFile
            activeStartedAt = startedAt
            recording = pendingRecording.start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    handleFinalize(event)
                }
            }
            ActiveCameraRecording(outputFile, profile, startedAt)
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.CameraUnavailable(it.message ?: "CameraX start failed")) },
        )
    }

    override suspend fun stopRecording(): DashCamResult<CompletedCameraRecording> =
        suspendCoroutine { continuation ->
            val activeRecording = recording
            if (activeRecording == null) {
                continuation.resume(
                    DashCamResult.Failure(DashCamError.InvalidState("idle", "No CameraX recording is active")),
                )
                return@suspendCoroutine
            }
            finalizeContinuation = continuation
            activeRecording.stop()
        }

    override suspend fun takePhoto(
        outputFile: File,
        mode: RecordingMode,
    ): DashCamResult<CompletedCameraPhoto> {
        if (recording != null) {
            return saveCurrentPreviewFrame(outputFile)
        }
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return DashCamResult.Failure(DashCamError.PermissionDenied(Manifest.permission.CAMERA))
        }

        return runCatching {
            val image = ensureImageCapture()
            suspendCoroutine<DashCamResult<CompletedCameraPhoto>> { continuation ->
                image.takePicture(
                    ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val resolutionInfo = image.resolutionInfo?.resolution
                            continuation.resume(
                                DashCamResult.Success(
                                    CompletedCameraPhoto(
                                        file = outputFile,
                                        createdAt = clock(),
                                        resolution = resolutionInfo?.let {
                                            DashCamResolution(width = it.width, height = it.height)
                                        },
                                    ),
                                ),
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resume(
                                DashCamResult.Failure(
                                    DashCamError.CameraUnavailable(exception.message ?: "Image capture failed"),
                                ),
                            )
                        }
                    },
                )
            }
        }.getOrElse {
            DashCamResult.Failure(DashCamError.CameraUnavailable(it.message ?: "Image capture failed"))
        }
    }

    private fun saveCurrentPreviewFrame(outputFile: File): DashCamResult<CompletedCameraPhoto> {
        val frame = lastPreviewJpeg.get()
            ?: return DashCamResult.Failure(
                DashCamError.InvalidState("recording", "No live preview frame is available for photo capture"),
            )
        return runCatching {
            outputFile.writeBytes(frame)
            CompletedCameraPhoto(
                file = outputFile,
                createdAt = clock(),
                resolution = DashCamResolution(width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT),
            )
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.StorageUnavailable(it.message ?: "Photo path is not writable")) },
        )
    }

    private fun obtainCameraProvider(): ProcessCameraProvider =
        cameraProvider ?: ProcessCameraProvider.getInstance(context).get().also { cameraProvider = it }

    private fun bindRecordingUseCases(
        provider: ProcessCameraProvider,
        capture: VideoCapture<Recorder>,
        preview: Preview?,
        analysis: ImageAnalysis,
    ) {
        val bound = when {
            preview != null && bindUseCases(provider, capture, preview, analysis) -> {
                imageAnalysis = analysis
                true
            }

            preview != null && bindUseCases(provider, capture, preview) -> {
                imageAnalysis = null
                true
            }

            bindUseCases(provider, capture, analysis) -> {
                imageAnalysis = analysis
                true
            }

            else -> false
        }
        if (!bound) {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, capture)
            imageAnalysis = null
        }
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        capture: VideoCapture<Recorder>,
        vararg useCases: androidx.camera.core.UseCase,
    ): Boolean =
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, capture, *useCases)
        }.isSuccess

    private fun ensureImageCapture(): ImageCapture {
        imageCapture?.let { return it }
        val provider = obtainCameraProvider()
        val image = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, image)
        imageCapture = image
        return image
    }

    private fun handleFinalize(event: VideoRecordEvent.Finalize) {
        val continuation = finalizeContinuation
        val file = activeFile
        val startedAt = activeStartedAt
        val durationMs = startedAt?.let { clock().toEpochMilli() - it.toEpochMilli() } ?: 0L

        val result = when {
            file == null || startedAt == null -> DashCamResult.Failure(
                DashCamError.InvalidState("finalized_without_active_file"),
            )

            event.hasError() -> event.toDashCamFailure()
            else -> DashCamResult.Success(
                CompletedCameraRecording(file = file, startedAt = startedAt, durationMs = durationMs),
            )
        }

        recording = null
        activeFile = null
        activeStartedAt = null
        finalizeContinuation = null
        continuation?.resume(result)
    }

    private fun analyzePreviewFrame(imageProxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastPreviewFrameAt.get() < PREVIEW_FRAME_INTERVAL_MS) return
            lastPreviewFrameAt.set(now)
            runCatching {
                imageProxy.toJpegBytes(PREVIEW_JPEG_QUALITY)?.let { frame ->
                    lastPreviewJpeg.set(frame)
                    onPreviewFrame(frame)
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun ImageProxy.toJpegBytes(quality: Int): ByteArray? {
        if (format != ImageFormat.YUV_420_888 || planes.size < 3) return null
        val nv21 = toNv21()
        return ByteArrayOutputStream().use { output ->
            YuvImage(nv21, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), quality, output)
            output.toByteArray()
        }
    }

    private fun ImageProxy.toNv21(): ByteArray {
        val bytes = ByteArray(width * height * 3 / 2)
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        var outputOffset = 0

        for (row in 0 until height) {
            for (col in 0 until width) {
                bytes[outputOffset++] = yPlane.buffer.get(row * yPlane.rowStride + col * yPlane.pixelStride)
            }
        }

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                bytes[outputOffset++] = vPlane.buffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                bytes[outputOffset++] = uPlane.buffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        return bytes
    }

    private fun VideoRecordEvent.Finalize.toDashCamFailure(): DashCamResult.Failure =
        when (error) {
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
            -> DashCamResult.Failure(
                DashCamError.UnsupportedEncoder("CameraX", cause?.message ?: "CameraX encoder failed"),
            )

            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> DashCamResult.Failure(
                DashCamError.StorageUnavailable(cause?.message ?: "Insufficient storage"),
            )

            else -> DashCamResult.Failure(
                DashCamError.CameraUnavailable(cause?.message ?: "CameraX recording failed with error $error"),
            )
        }

    private fun DashCamVideoQuality.toQualitySelector(): QualitySelector {
        val quality = when (this) {
            DashCamVideoQuality.FHD -> Quality.FHD
            DashCamVideoQuality.HD -> Quality.HD
            DashCamVideoQuality.SD -> Quality.SD
        }
        return QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
    }

    private fun PendingRecording.withAudioIfNeeded(audioEnabled: Boolean): PendingRecording =
        if (audioEnabled) {
            withAudioEnabled()
        } else {
            this
        }

    companion object {
        private const val PREVIEW_WIDTH = 320
        private const val PREVIEW_HEIGHT = 180
        private const val PREVIEW_FRAME_INTERVAL_MS = 200L
        private const val PREVIEW_JPEG_QUALITY = 55
    }
}
