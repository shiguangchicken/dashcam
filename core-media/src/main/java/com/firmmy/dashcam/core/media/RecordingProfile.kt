package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.RecordingMode

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
}
