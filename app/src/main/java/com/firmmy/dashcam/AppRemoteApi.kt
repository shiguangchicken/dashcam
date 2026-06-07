package com.firmmy.dashcam

import android.content.Context
import androidx.core.content.ContextCompat
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.database.SettingsRepository
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.core.network.DashCamRemoteCommandDispatcher
import com.firmmy.dashcam.core.network.DashCamRemoteDataSource
import com.firmmy.dashcam.core.network.RemoteMediaAsset
import com.firmmy.dashcam.core.network.RemoteMediaItem
import com.firmmy.dashcam.core.network.RemoteSettings
import com.firmmy.dashcam.core.network.RemoteStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class AppRemoteDataSource(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val dashCamMediaRepository: DashCamMediaRepository,
    private val directories: DashCamMediaDirectories,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DashCamRemoteDataSource {
    override suspend fun status(): RemoteStatus {
        val settings = settingsRepository.getSettings()
        val root = directories.ensureBaseDirectories().root
        val runtimeStatus = RecorderRuntimeState.status()
        return runtimeStatus.copy(
            audioEnabled = settings.audioEnabled,
            hotspotEnabled = settings.hotspotSsid.isNotBlank(),
            hotspotSsid = settings.hotspotSsid,
            freeSpaceBytes = root.usableSpace,
            liveStreamAvailable = RecorderRuntimeState.livePreviewFrame() != null &&
                runtimeStatus.recordingStatus != RecordingStatus.IDLE,
        )
    }

    override suspend fun listMedia(
        type: MediaType?,
        date: String?,
    ): List<RemoteMediaItem> {
        val dayRange = date?.toEpochRange()
        return mediaRepository.observeFilteredMedia(
            type = type,
            startCreatedAt = dayRange?.first,
            endCreatedAt = dayRange?.second,
        ).first().mapNotNull(MediaFileEntity::toRemoteMediaItem)
    }

    override suspend fun mediaThumbnail(id: Long): RemoteMediaAsset? {
        val media = mediaRepository.getMediaFile(id)?.takeIf { !it.deleted } ?: return null
        val thumbnailPath = media.thumbnailPath ?: return null
        return File(thumbnailPath).toSafeAsset(contentType = "image/jpeg")
    }

    override suspend fun mediaStream(id: Long): RemoteMediaAsset? {
        val media = mediaRepository.getMediaFile(id)?.takeIf { !it.deleted } ?: return null
        val type = MediaType.fromStoredValue(media.type) ?: return null
        val contentType = when (type) {
            MediaType.VIDEO -> "video/mp4"
            MediaType.PHOTO -> "image/jpeg"
        }
        return File(media.path).toSafeAsset(contentType = contentType)
    }

    override suspend fun livePreviewFrame(): ByteArray? = RecorderRuntimeState.livePreviewFrame()

    override suspend fun deleteMedia(id: Long): Boolean =
        dashCamMediaRepository.deleteMedia(id).isSuccess

    override suspend fun settings(): RemoteSettings =
        settingsRepository.getSettings().toRemoteSettings()

    override suspend fun saveSettings(settings: RemoteSettings): Boolean {
        val current = settingsRepository.getSettings()
        settingsRepository.saveSettings(settings.toDashCamSettings(current))
        return true
    }

    private fun File.toSafeAsset(contentType: String): RemoteMediaAsset? {
        val root = directories.ensureBaseDirectories().root.canonicalFile
        val file = canonicalFile
        if (!file.path.startsWith(root.path) || !file.isFile) return null
        return RemoteMediaAsset(file = file, contentType = contentType)
    }

    private fun String.toEpochRange(): Pair<Long, Long>? =
        runCatching {
            val start = LocalDate.parse(this).atStartOfDay(zoneId).toInstant()
            start.toEpochMilli() to start.plusSeconds(24 * 60 * 60).toEpochMilli()
        }.getOrNull()
}

class AppRemoteCommandDispatcher(
    private val context: Context,
    private val onHotspotCommand: (DashCamCommand) -> Boolean = { false },
) : DashCamRemoteCommandDispatcher {
    override suspend fun dispatch(command: DashCamCommand): Boolean {
        val action = when (command) {
            DashCamCommand.StartDrivingMode -> RecorderForegroundService.ACTION_SWITCH_DRIVING
            DashCamCommand.StartParkingMode -> RecorderForegroundService.ACTION_SWITCH_PARKING
            DashCamCommand.TakePhoto -> RecorderForegroundService.ACTION_TAKE_PHOTO
            DashCamCommand.StopRecording -> RecorderForegroundService.ACTION_STOP
            DashCamCommand.EnableAudio -> RecorderForegroundService.ACTION_ENABLE_AUDIO
            DashCamCommand.DisableAudio -> RecorderForegroundService.ACTION_DISABLE_AUDIO
            DashCamCommand.StartHotspot,
            DashCamCommand.StopHotspot,
            -> return onHotspotCommand(command)

            DashCamCommand.LockCurrentClip,
            -> return false
        }
        ContextCompat.startForegroundService(
            context.applicationContext,
            RecorderForegroundService.commandIntent(context.applicationContext, action),
        )
        return true
    }
}

private fun MediaFileEntity.toRemoteMediaItem(): RemoteMediaItem? {
    val type = MediaType.fromStoredValue(type) ?: return null
    val mode = RecordingMode.fromStoredValue(mode) ?: RecordingMode.MANUAL
    return RemoteMediaItem(
        id = id,
        type = type,
        mode = mode,
        path = path,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        fps = fps,
        bitrate = bitrate,
        hasAudio = hasAudio,
        locked = locked,
    )
}

private fun DashCamSettings.toRemoteSettings(): RemoteSettings =
    RemoteSettings(
        drivingResolution = drivingResolution,
        drivingFps = drivingFps,
        drivingBitrateKbps = drivingBitrateKbps,
        parkingResolution = parkingResolution,
        parkingFps = parkingFps,
        parkingBitrateKbps = parkingBitrateKbps,
        segmentDurationMinutes = segmentDurationMinutes,
        maxStorageGb = maxStorageGb,
        minFreeSpaceGb = minFreeSpaceGb,
        audioEnabled = audioEnabled,
        voiceWakeupEnabled = voiceWakeupEnabled,
        wakeWord = wakeWord,
    )

private fun RemoteSettings.toDashCamSettings(current: DashCamSettings): DashCamSettings =
    current.copy(
        drivingResolution = drivingResolution,
        drivingFps = drivingFps,
        drivingBitrateKbps = drivingBitrateKbps,
        parkingResolution = parkingResolution,
        parkingFps = parkingFps,
        parkingBitrateKbps = parkingBitrateKbps,
        segmentDurationMinutes = segmentDurationMinutes,
        maxStorageGb = maxStorageGb,
        minFreeSpaceGb = minFreeSpaceGb,
        audioEnabled = audioEnabled,
        voiceWakeupEnabled = voiceWakeupEnabled,
        wakeWord = wakeWord,
    )
