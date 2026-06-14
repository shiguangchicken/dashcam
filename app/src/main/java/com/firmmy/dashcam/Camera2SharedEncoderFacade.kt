package com.firmmy.dashcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.media.ActiveCameraRecording
import com.firmmy.dashcam.core.media.CameraFpsRange
import com.firmmy.dashcam.core.media.CameraFpsSelector
import com.firmmy.dashcam.core.media.CameraRecordingFacade
import com.firmmy.dashcam.core.media.CompletedCameraPhoto
import com.firmmy.dashcam.core.media.CompletedCameraRecording
import com.firmmy.dashcam.core.media.RecordingProfile
import com.firmmy.dashcam.core.network.H264AccessUnit
import com.firmmy.dashcam.core.network.H264FrameFlags
import com.firmmy.dashcam.core.network.H264LiveStream
import com.firmmy.dashcam.core.network.H264StreamConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Camera2SharedEncoderFacade(
    private val context: Context,
    private val previewSurfaceProvider: () -> Surface? = { null },
    private val liveStream: H264LiveStream,
    private val clock: () -> Instant = { Instant.now() },
) : CameraRecordingFacade {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val lock = Any()

    private var active: ActiveSession? = null
    private var lastEncodedFrame: ByteArray? = null

    override suspend fun startRecording(
        outputFile: File,
        profile: RecordingProfile,
    ): DashCamResult<ActiveCameraRecording> {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return DashCamResult.Failure(DashCamError.PermissionDenied(Manifest.permission.CAMERA))
        }
        if (active != null) {
            return DashCamResult.Failure(DashCamError.InvalidState("recording", "Recording is already active"))
        }

        return runCatching {
            val cameraId = cameraManager.cameraIdList.first()
            val cameraThread = HandlerThread("dashcam-camera2").apply { start() }
            val encoderThread = HandlerThread("dashcam-h264-encoder").apply { start() }
            val cameraHandler = Handler(cameraThread.looper)
            val encoderHandler = Handler(encoderThread.looper)
            val encoder = createVideoEncoder(profile)
            val inputSurface = encoder.createInputSurface()
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val rotationDegrees = sensorOrientation(cameraId)
            muxer.setOrientationHint(rotationDegrees)
            val fpsRange = selectFpsRange(cameraId, profile.fps)
            val previewSurface = previewSurfaceProvider()?.takeIf { it.isValid }
            val camera = openCamera(cameraId, cameraHandler)
            val session = createSession(
                camera = camera,
                handler = cameraHandler,
                encoderSurface = inputSurface,
                previewSurface = previewSurface,
                fpsRange = fpsRange,
            )
            val state = ActiveSession(
                outputFile = outputFile,
                profile = profile.copy(fps = fpsRange.upper),
                startedAt = clock(),
                camera = camera,
                session = session,
                encoder = encoder,
                encoderInputSurface = inputSurface,
                muxer = muxer,
                rotationDegrees = rotationDegrees,
                cameraThread = cameraThread,
                encoderThread = encoderThread,
            )
            active = state
            encoder.start()
            encoderHandler.post { drainEncoder(state) }
            ActiveCameraRecording(outputFile, state.profile, state.startedAt)
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { throwable ->
                cleanupActive()
                DashCamResult.Failure(DashCamError.CameraUnavailable(throwable.message ?: "Camera2 start failed"))
            },
        )
    }

    override suspend fun stopRecording(): DashCamResult<CompletedCameraRecording> =
        suspendCoroutine { continuation ->
            val session = synchronized(lock) { active }
            if (session == null) {
                continuation.resume(
                    DashCamResult.Failure(DashCamError.InvalidState("idle", "No Camera2 recording is active")),
                )
                return@suspendCoroutine
            }
            session.onCompleted = {
                val durationMs = clock().toEpochMilli() - session.startedAt.toEpochMilli()
                cleanupSession(session)
                synchronized(lock) {
                    if (active === session) active = null
                }
                continuation.resume(
                    DashCamResult.Success(
                        CompletedCameraRecording(
                            file = session.outputFile,
                            startedAt = session.startedAt,
                            durationMs = durationMs,
                        ),
                    ),
                )
            }
            session.stopping.set(true)
            runCatching { session.encoder.signalEndOfInputStream() }
        }

    override suspend fun takePhoto(
        outputFile: File,
        mode: RecordingMode,
    ): DashCamResult<CompletedCameraPhoto> {
        val encodedFrame = lastEncodedFrame
            ?: return DashCamResult.Failure(
                DashCamError.InvalidState("recording", "No encoded preview frame is available for photo capture"),
            )
        return runCatching {
            outputFile.writeBytes(encodedFrame)
            CompletedCameraPhoto(outputFile, clock(), null)
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.StorageUnavailable(it.message ?: "Photo path is not writable")) },
        )
    }

    private fun createVideoEncoder(profile: RecordingProfile): MediaCodec {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, profile.resolution.width, profile.resolution.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, profile.bitrateKbps * 1_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, profile.fps.coerceAtLeast(1))
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEYFRAME_INTERVAL_SECONDS)
        return MediaCodec.createEncoderByType(MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun openCamera(cameraId: String, handler: Handler): CameraDevice {
        val latch = CountDownLatch(1)
        var camera: CameraDevice? = null
        var error: Throwable? = null
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            error = SecurityException("Camera permission denied")
            latch.countDown()
        } else {
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        camera = device
                        latch.countDown()
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        error = IllegalStateException("Camera disconnected")
                        latch.countDown()
                    }

                    override fun onError(device: CameraDevice, code: Int) {
                        device.close()
                        error = IllegalStateException("Camera open failed with code $code")
                        latch.countDown()
                    }
                },
                handler,
            )
        }
        check(latch.await(CAMERA_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Timed out opening camera" }
        error?.let { throw it }
        return checkNotNull(camera) { "Camera did not open" }
    }

    private fun createSession(
        camera: CameraDevice,
        handler: Handler,
        encoderSurface: Surface,
        previewSurface: Surface?,
        fpsRange: CameraFpsRange,
    ): CameraCaptureSession {
        val targets = buildList {
            add(encoderSurface)
            previewSurface?.let { add(it) }
        }
        val latch = CountDownLatch(1)
        var session: CameraCaptureSession? = null
        var error: Throwable? = null
        camera.createCaptureSession(
            targets,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configuredSession: CameraCaptureSession) {
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        targets.forEach(::addTarget)
                        set(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(fpsRange.lower, fpsRange.upper),
                        )
                    }.build()
                    configuredSession.setRepeatingRequest(request, null, handler)
                    session = configuredSession
                    latch.countDown()
                }

                override fun onConfigureFailed(failedSession: CameraCaptureSession) {
                    error = IllegalStateException("Camera capture session configure failed")
                    latch.countDown()
                }
            },
            handler,
        )
        check(latch.await(CAMERA_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Timed out configuring camera" }
        error?.let { throw it }
        return checkNotNull(session) { "Camera capture session did not configure" }
    }

    private fun selectFpsRange(cameraId: String, preferredFps: Int): CameraFpsRange {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val ranges = characteristics
            .get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { CameraFpsRange(it.lower, it.upper) }
            .orEmpty()
        return CameraFpsSelector.select(ranges, preferredFps = preferredFps.coerceAtLeast(1))
    }

    private fun sensorOrientation(cameraId: String): Int =
        cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION)
            ?.let { ((it % 360) + 360) % 360 }
            ?: 0

    private fun drainEncoder(session: ActiveSession) {
        val bufferInfo = MediaCodec.BufferInfo()
        var muxerStarted = false
        var videoTrack = -1
        var streamConfig: H264StreamConfig? = null

        while (true) {
            val index = session.encoder.dequeueOutputBuffer(bufferInfo, ENCODER_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (session.stopping.get()) continue
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = session.encoder.outputFormat
                    videoTrack = session.muxer.addTrack(format)
                    session.muxer.start()
                    muxerStarted = true
                    streamConfig = format.toStreamConfig(
                        profile = session.profile,
                        rotationDegrees = session.rotationDegrees,
                    ).also(liveStream::publishConfig)
                }

                index >= 0 -> {
                    val output = session.encoder.getOutputBuffer(index)
                    if (output != null && bufferInfo.size > 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        if (muxerStarted && videoTrack >= 0) {
                            session.muxer.writeSampleData(videoTrack, output.duplicate(), bufferInfo)
                        }
                        val bytes = ByteArray(bufferInfo.size)
                        output.duplicate().get(bytes)
                        if (streamConfig != null) {
                            val flags = if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                                H264FrameFlags.KEYFRAME
                            } else {
                                0
                            }
                            liveStream.publishFrame(
                                H264AccessUnit(
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    flags = flags,
                                    payload = bytes.toAnnexB(),
                                ),
                            )
                            lastEncodedFrame = bytes
                        }
                    }
                    val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    session.encoder.releaseOutputBuffer(index, false)
                    if (endOfStream) {
                        session.onCompleted?.invoke()
                        return
                    }
                }
            }
        }
    }

    private fun MediaFormat.toStreamConfig(
        profile: RecordingProfile,
        rotationDegrees: Int,
    ): H264StreamConfig {
        val sps = getByteBuffer("csd-0")?.toByteArray() ?: byteArrayOf()
        val pps = getByteBuffer("csd-1")?.toByteArray() ?: byteArrayOf()
        return H264StreamConfig(
            width = profile.resolution.width,
            height = profile.resolution.height,
            fps = profile.fps,
            rotationDegrees = rotationDegrees,
            sps = sps.toAnnexB(),
            pps = pps.toAnnexB(),
        )
    }

    private fun ByteArray.toAnnexB(): ByteArray {
        if (size >= 4 && this[0] == 0.toByte() && this[1] == 0.toByte() && this[2] == 0.toByte() && this[3] == 1.toByte()) {
            return this
        }
        val output = ByteArrayOutputStream(size + 4)
        var offset = 0
        while (offset + 4 <= size) {
            val nalSize = ((this[offset].toInt() and 0xff) shl 24) or
                ((this[offset + 1].toInt() and 0xff) shl 16) or
                ((this[offset + 2].toInt() and 0xff) shl 8) or
                (this[offset + 3].toInt() and 0xff)
            if (nalSize <= 0 || offset + 4 + nalSize > size) {
                return byteArrayOf(0, 0, 0, 1) + this
            }
            output.write(byteArrayOf(0, 0, 0, 1))
            output.write(this, offset + 4, nalSize)
            offset += 4 + nalSize
        }
        return output.toByteArray()
    }

    private fun java.nio.ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        duplicate.position(0)
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    private fun cleanupActive() {
        synchronized(lock) {
            active?.let(::cleanupSession)
            active = null
        }
    }

    private fun cleanupSession(session: ActiveSession) {
        runCatching { session.session.stopRepeating() }
        runCatching { session.session.close() }
        runCatching { session.camera.close() }
        runCatching { session.encoder.stop() }
        runCatching { session.encoder.release() }
        runCatching { session.encoderInputSurface.release() }
        runCatching { session.muxer.stop() }
        runCatching { session.muxer.release() }
        session.cameraThread.quitSafely()
        session.encoderThread.quitSafely()
    }

    private data class ActiveSession(
        val outputFile: File,
        val profile: RecordingProfile,
        val startedAt: Instant,
        val camera: CameraDevice,
        val session: CameraCaptureSession,
        val encoder: MediaCodec,
        val encoderInputSurface: Surface,
        val muxer: MediaMuxer,
        val rotationDegrees: Int,
        val cameraThread: HandlerThread,
        val encoderThread: HandlerThread,
        val stopping: AtomicBoolean = AtomicBoolean(false),
        var onCompleted: (() -> Unit)? = null,
    )

    companion object {
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val KEYFRAME_INTERVAL_SECONDS = 1
        private const val CAMERA_OPEN_TIMEOUT_SECONDS = 5L
        private const val ENCODER_TIMEOUT_US = 10_000L
    }
}
