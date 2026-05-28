package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import java.io.File
import java.time.Instant

data class ActiveCameraRecording(
    val file: File,
    val profile: RecordingProfile,
    val startedAt: Instant,
)

data class CompletedCameraRecording(
    val file: File,
    val startedAt: Instant,
    val durationMs: Long,
)

data class CompletedCameraPhoto(
    val file: File,
    val createdAt: Instant,
    val resolution: DashCamResolution? = null,
)

interface CameraRecordingFacade {
    suspend fun startRecording(
        outputFile: File,
        profile: RecordingProfile,
    ): DashCamResult<ActiveCameraRecording>

    suspend fun stopRecording(): DashCamResult<CompletedCameraRecording>

    suspend fun takePhoto(
        outputFile: File,
        mode: RecordingMode,
    ): DashCamResult<CompletedCameraPhoto>
}
