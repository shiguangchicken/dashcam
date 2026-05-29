package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.DashCamSettings

data class DashCamResolution(
    val width: Int,
    val height: Int,
)

enum class DashCamVideoQuality {
    FHD,
    HD,
    SD,
}

data class RecordingProfile(
    val mode: RecordingMode,
    val resolution: DashCamResolution,
    val fps: Int,
    val bitrateKbps: Int,
    val audioEnabled: Boolean,
    val quality: DashCamVideoQuality,
)

object RecordingProfiles {
    fun driving(audioEnabled: Boolean = true): RecordingProfile =
        RecordingProfile(
            mode = RecordingMode.DRIVING,
            resolution = DashCamResolution(width = 1920, height = 1080),
            fps = 30,
            bitrateKbps = 12_000,
            audioEnabled = audioEnabled,
            quality = DashCamVideoQuality.FHD,
        )

    fun parking(audioEnabled: Boolean = false): RecordingProfile =
        RecordingProfile(
            mode = RecordingMode.PARKING,
            resolution = DashCamResolution(width = 1280, height = 720),
            fps = 2,
            bitrateKbps = 1_000,
            audioEnabled = audioEnabled,
            quality = DashCamVideoQuality.HD,
        )

    fun driving(settings: DashCamSettings): RecordingProfile {
        val resolution = settings.drivingResolution.toDashCamResolution()
        return RecordingProfile(
            mode = RecordingMode.DRIVING,
            resolution = resolution,
            fps = settings.drivingFps,
            bitrateKbps = settings.drivingBitrateKbps,
            audioEnabled = settings.audioEnabled,
            quality = resolution.toVideoQuality(),
        )
    }

    fun parking(settings: DashCamSettings): RecordingProfile {
        val resolution = settings.parkingResolution.toDashCamResolution()
        return RecordingProfile(
            mode = RecordingMode.PARKING,
            resolution = resolution,
            fps = settings.parkingFps,
            bitrateKbps = settings.parkingBitrateKbps,
            audioEnabled = settings.audioEnabled,
            quality = resolution.toVideoQuality(),
        )
    }

    private fun String.toDashCamResolution(): DashCamResolution {
        val parts = split("x")
        val width = parts.getOrNull(0)?.toIntOrNull() ?: 1280
        val height = parts.getOrNull(1)?.toIntOrNull() ?: 720
        return DashCamResolution(width = width, height = height)
    }

    private fun DashCamResolution.toVideoQuality(): DashCamVideoQuality =
        when {
            width >= 1920 || height >= 1080 -> DashCamVideoQuality.FHD
            width >= 1280 || height >= 720 -> DashCamVideoQuality.HD
            else -> DashCamVideoQuality.SD
        }
}
