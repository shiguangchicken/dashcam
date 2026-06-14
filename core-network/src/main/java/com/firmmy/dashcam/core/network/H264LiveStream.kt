package com.firmmy.dashcam.core.network

import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.firmmy.dashcam.core.common.DashCamLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class H264StreamConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val rotationDegrees: Int = 0,
    val sps: ByteArray,
    val pps: ByteArray,
    val codec: String = "h264",
    val format: String = "annex-b",
) {
    fun toJson(): String {
        val encoder = Base64.getEncoder()
        return buildString {
            append("{")
            append("\"codec\":\"").append(codec).append("\",")
            append("\"width\":").append(width).append(",")
            append("\"height\":").append(height).append(",")
            append("\"fps\":").append(fps).append(",")
            append("\"rotationDegrees\":").append(rotationDegrees).append(",")
            append("\"format\":\"").append(format).append("\",")
            append("\"sps\":\"").append(encoder.encodeToString(sps)).append("\",")
            append("\"pps\":\"").append(encoder.encodeToString(pps)).append("\"")
            append("}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is H264StreamConfig) return false
        return width == other.width &&
            height == other.height &&
            fps == other.fps &&
            rotationDegrees == other.rotationDegrees &&
            codec == other.codec &&
            format == other.format &&
            sps.contentEquals(other.sps) &&
            pps.contentEquals(other.pps)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + fps
        result = 31 * result + rotationDegrees
        result = 31 * result + sps.contentHashCode()
        result = 31 * result + pps.contentHashCode()
        result = 31 * result + codec.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}

data class H264AccessUnit(
    val presentationTimeUs: Long,
    val flags: Int,
    val payload: ByteArray,
) {
    val isKeyframe: Boolean
        get() = flags and H264FrameFlags.KEYFRAME != 0

    fun toWebSocketPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + payload.size)
        buffer.putLong(presentationTimeUs)
        buffer.put(flags.toByte())
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is H264AccessUnit) return false
        return presentationTimeUs == other.presentationTimeUs &&
            flags == other.flags &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = presentationTimeUs.hashCode()
        result = 31 * result + flags
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE_BYTES = 13
    }
}

object H264FrameFlags {
    const val KEYFRAME = 0x01
    const val CODEC_CONFIG = 0x02
}

sealed interface H264LiveStreamEvent {
    data class Config(val value: H264StreamConfig) : H264LiveStreamEvent
    data class Frame(val value: H264AccessUnit) : H264LiveStreamEvent
}

class H264LiveStream {
    private val latestConfig = AtomicReference<H264StreamConfig?>(null)
    private val nextSubscriberId = AtomicInteger(1)
    private val subscribers = ConcurrentHashMap<Int, Channel<H264LiveStreamEvent>>()

    val available: Boolean
        get() = latestConfig.get() != null

    fun currentConfig(): H264StreamConfig? = latestConfig.get()

    fun publishConfig(config: H264StreamConfig) {
        latestConfig.set(config)
        subscribers.values.forEach { channel ->
            channel.trySend(H264LiveStreamEvent.Config(config))
        }
    }

    fun publishFrame(frame: H264AccessUnit) {
        subscribers.values.forEach { channel ->
            channel.trySend(H264LiveStreamEvent.Frame(frame))
        }
    }

    fun reset() {
        latestConfig.set(null)
        val activeSubscribers = subscribers.size
        subscribers.values.forEach { channel -> channel.close() }
        subscribers.clear()
        DashCamLog.info(H264_STREAM_LOG_TAG, "reset live stream; closedSubscribers=$activeSubscribers")
    }

    fun subscribe(): Flow<H264LiveStreamEvent> =
        callbackFlow {
            val id = nextSubscriberId.getAndIncrement()
            val channel = Channel<H264LiveStreamEvent>(Channel.CONFLATED)
            subscribers[id] = channel
            DashCamLog.info(H264_STREAM_LOG_TAG, "subscriber added id=$id count=${subscribers.size}")
            latestConfig.get()?.let { trySend(H264LiveStreamEvent.Config(it)) }
            val forwarder = this.launch {
                for (event in channel) {
                    send(event)
                }
                close()
            }
            awaitClose {
                subscribers.remove(id)
                channel.close()
                forwarder.cancel()
                DashCamLog.info(H264_STREAM_LOG_TAG, "subscriber removed id=$id count=${subscribers.size}")
            }
        }
}

private const val H264_STREAM_LOG_TAG = "H264LiveStream"
