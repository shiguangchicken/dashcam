package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import java.io.File

data class RemoteStatus(
    val recordingStatus: RecordingStatus = RecordingStatus.IDLE,
    val mode: RecordingMode = RecordingMode.DRIVING,
    val audioEnabled: Boolean = true,
    val hotspotEnabled: Boolean = false,
    val hotspotSsid: String = "",
    val freeSpaceBytes: Long = 0L,
    val speedKmh: Int? = null,
    val batteryPercent: Int? = null,
    val temperatureCelsius: Float? = null,
    val liveStreamAvailable: Boolean = false,
    val remoteViewers: List<RemoteViewerClientInfo> = emptyList(),
)

data class RemoteViewerClientInfo(
    val id: String,
    val name: String,
    val lastSeenEpochMillis: Long,
)

data class RemoteMediaItem(
    val id: Long,
    val type: MediaType,
    val mode: RecordingMode,
    val path: String,
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val durationMs: Long? = null,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val bitrate: Int? = null,
    val hasAudio: Boolean = false,
    val locked: Boolean = false,
)

data class RemoteSettings(
    val drivingResolution: String,
    val drivingFps: Int,
    val drivingBitrateKbps: Int,
    val parkingResolution: String,
    val parkingFps: Int,
    val parkingBitrateKbps: Int,
    val segmentDurationMinutes: Int,
    val maxStorageGb: Int,
    val minFreeSpaceGb: Int,
    val audioEnabled: Boolean,
    val voiceWakeupEnabled: Boolean,
    val wakeWord: String,
)

data class RemoteCommandRequest(
    val command: DashCamCommand,
)

data class RemoteApiResponse(
    val ok: Boolean,
    val message: String = "",
)

data class RemoteMediaAsset(
    val file: File,
    val contentType: String,
    val downloadName: String = file.name,
)

interface DashCamRemoteDataSource {
    suspend fun status(): RemoteStatus

    suspend fun listMedia(
        type: MediaType?,
        date: String?,
    ): List<RemoteMediaItem>

    suspend fun mediaThumbnail(id: Long): RemoteMediaAsset?

    suspend fun mediaStream(id: Long): RemoteMediaAsset?

    suspend fun mediaDownload(id: Long): RemoteMediaAsset? = mediaStream(id)

    suspend fun livePreviewFrame(): ByteArray? = null

    fun liveH264Stream(): H264LiveStream? = null

    suspend fun deleteMedia(id: Long): Boolean

    suspend fun settings(): RemoteSettings

    suspend fun saveSettings(settings: RemoteSettings): Boolean
}

interface DashCamRemoteCommandDispatcher {
    suspend fun dispatch(command: DashCamCommand): Boolean
}

sealed interface RemoteEvent {
    data class StatusChanged(val status: RemoteStatus) : RemoteEvent
    data class MediaCreated(val media: RemoteMediaItem) : RemoteEvent
    data class CommandHandled(val command: DashCamCommand, val ok: Boolean) : RemoteEvent
}
