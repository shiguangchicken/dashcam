package com.firmmy.dashcam.feature.remote

import android.graphics.SurfaceTexture
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
internal fun H264RemoteLiveSurface(
    url: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val controller = remember { H264RemoteDecoderController() }
    DisposableEffect(url, active) {
        onDispose {
            controller.stop()
        }
    }
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote_h264_live_surface"),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        surface = Surface(texture).also {
                            tag = it
                            if (active && url.isNotBlank()) {
                                controller.start(url, it, this@apply)
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        controller.applyCurrentTransform(this@apply)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        controller.stop()
                        surface?.release()
                        tag = null
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            if (view.isAvailable && active && url.isNotBlank()) {
                (view.tag as? Surface)?.let { controller.start(url, it, view) }
            } else if (!active) {
                controller.stop()
            }
        },
    )
}

private class H264RemoteDecoderController {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }
    private val activeJob = AtomicReference<Job?>(null)
    private val decoder = AtomicReference<MediaCodec?>(null)
    private val currentConfig = AtomicReference<H264StreamConfig?>(null)

    fun start(
        url: String,
        surface: Surface,
        textureView: TextureView,
    ) {
        val existing = activeJob.get()
        if (existing?.isActive == true) return
        val job = scope.launch {
            runCatching {
                client.webSocket(url) {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> configureDecoder(frame.readText(), surface, textureView)
                            is Frame.Binary -> queueAccessUnit(frame.readBytes())
                            else -> Unit
                        }
                    }
                }
            }
            releaseDecoder()
        }
        activeJob.set(job)
    }

    fun stop() {
        val job = activeJob.getAndSet(null)
        scope.launch {
            job?.cancelAndJoin()
            releaseDecoder()
        }
    }

    private fun configureDecoder(
        configJson: String,
        surface: Surface,
        textureView: TextureView,
    ) {
        releaseDecoder()
        val config = H264StreamConfig.fromJson(configJson)
        currentConfig.set(config)
        textureView.post { applyCurrentTransform(textureView) }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
        if (config.sps.isNotEmpty()) {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(config.sps))
        }
        if (config.pps.isNotEmpty()) {
            format.setByteBuffer("csd-1", ByteBuffer.wrap(config.pps))
        }
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, surface, null, 0)
            it.start()
            decoder.set(it)
        }
    }

    private fun queueAccessUnit(message: ByteArray) {
        val codec = decoder.get() ?: return
        if (message.size < HEADER_SIZE) return
        val header = ByteBuffer.wrap(message)
        val presentationTimeUs = header.long
        header.get()
        val payloadSize = header.int
        if (payloadSize <= 0 || HEADER_SIZE + payloadSize > message.size) return
        val payload = message.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadSize)

        val inputIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
        if (inputIndex >= 0) {
            val input = codec.getInputBuffer(inputIndex) ?: return
            input.clear()
            input.put(payload)
            codec.queueInputBuffer(inputIndex, 0, payload.size, presentationTimeUs, 0)
        }
        drainDecoder(codec)
    }

    private fun drainDecoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex < 0) return
            codec.releaseOutputBuffer(outputIndex, true)
        }
    }

    private fun releaseDecoder() {
        decoder.getAndSet(null)?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
    }

    fun applyCurrentTransform(textureView: TextureView) {
        val config = currentConfig.get() ?: return
        if (textureView.width <= 0 || textureView.height <= 0) return
        textureView.setTransform(
            videoTransform(
                viewWidth = textureView.width.toFloat(),
                viewHeight = textureView.height.toFloat(),
                videoWidth = config.width.toFloat(),
                videoHeight = config.height.toFloat(),
                rotationDegrees = config.rotationDegrees,
            ),
        )
    }

    private data class H264StreamConfig(
        val width: Int,
        val height: Int,
        val fps: Int,
        val rotationDegrees: Int,
        val sps: ByteArray,
        val pps: ByteArray,
    ) {
        companion object {
            fun fromJson(json: String): H264StreamConfig =
                H264StreamConfig(
                    width = json.intValue("width") ?: 1280,
                    height = json.intValue("height") ?: 720,
                    fps = json.intValue("fps") ?: 30,
                    rotationDegrees = json.intValue("rotationDegrees") ?: 0,
                    sps = json.base64Value("sps"),
                    pps = json.base64Value("pps"),
                )
        }
    }

    companion object {
        private const val HEADER_SIZE = 13
        private const val DECODE_TIMEOUT_US = 10_000L
    }
}

private fun String.intValue(name: String): Int? =
    Regex("\"$name\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun String.base64Value(name: String): ByteArray =
    Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { Base64.decode(it, Base64.NO_WRAP) }
        ?: byteArrayOf()

private fun videoTransform(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float,
    rotationDegrees: Int,
): Matrix {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
    val rotated = normalizedRotation == 90 || normalizedRotation == 270
    val effectiveVideoWidth = if (rotated) videoHeight else videoWidth
    val effectiveVideoHeight = if (rotated) videoWidth else videoHeight
    val scale = max(viewWidth / effectiveVideoWidth, viewHeight / effectiveVideoHeight)
    val scaledWidth = effectiveVideoWidth * scale
    val scaledHeight = effectiveVideoHeight * scale
    val bufferRect = RectF(
        (viewWidth - scaledWidth) / 2f,
        (viewHeight - scaledHeight) / 2f,
        (viewWidth + scaledWidth) / 2f,
        (viewHeight + scaledHeight) / 2f,
    )
    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
    matrix.postRotate(normalizedRotation.toFloat(), viewWidth / 2f, viewHeight / 2f)
    return matrix
}
