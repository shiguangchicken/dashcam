package com.firmmy.dashcam

import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.network.H264LiveStream
import com.firmmy.dashcam.core.network.RemoteStatus
import java.util.concurrent.atomic.AtomicReference

object RecorderRuntimeState {
    private val status = AtomicReference(
        RemoteStatus(
            recordingStatus = RecordingStatus.IDLE,
            mode = RecordingMode.DRIVING,
        ),
    )
    private val h264LiveStream = H264LiveStream()

    fun updateStatus(
        recordingStatus: RecordingStatus,
        mode: RecordingMode,
        audioEnabled: Boolean,
        freeSpaceBytes: Long,
        hotspotEnabled: Boolean = status.get().hotspotEnabled,
        hotspotSsid: String = status.get().hotspotSsid,
    ) {
        status.set(
            RemoteStatus(
                recordingStatus = recordingStatus,
                mode = mode,
                audioEnabled = audioEnabled,
                hotspotEnabled = hotspotEnabled,
                hotspotSsid = hotspotSsid,
                freeSpaceBytes = freeSpaceBytes,
                liveStreamAvailable = isLiveStreamAvailable(recordingStatus),
            ),
        )
    }

    fun updateHotspot(
        enabled: Boolean,
        ssid: String,
    ) {
        val current = status.get()
        updateStatus(
            recordingStatus = current.recordingStatus,
            mode = current.mode,
            audioEnabled = current.audioEnabled,
            freeSpaceBytes = current.freeSpaceBytes,
            hotspotEnabled = enabled,
            hotspotSsid = ssid,
        )
    }

    fun clearLivePreviewFrame() {
        val current = status.get()
        status.set(current.copy(liveStreamAvailable = isLiveStreamAvailable(current.recordingStatus)))
    }

    fun status(): RemoteStatus = status.get()

    fun livePreviewFrame(): ByteArray? = null

    fun liveH264Stream(): H264LiveStream = h264LiveStream

    private fun isLiveStreamAvailable(recordingStatus: RecordingStatus): Boolean =
        recordingStatus != RecordingStatus.IDLE && h264LiveStream.available
}
