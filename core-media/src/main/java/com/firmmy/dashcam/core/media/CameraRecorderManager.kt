package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaFileEntity
import java.io.File
import java.time.Instant

class CameraRecorderManager(
    private val directories: DashCamMediaDirectories,
    private val mediaRepository: DashCamMediaRepository,
    private val cameraFacade: CameraRecordingFacade,
    private val clock: () -> Instant = { Instant.now() },
) {
    private var activeRecording: ActiveCameraRecording? = null

    suspend fun startDrivingRecording(audioEnabled: Boolean = true): DashCamResult<ActiveCameraRecording> =
        startRecording(RecordingProfiles.driving(audioEnabled = audioEnabled))

    suspend fun startParkingRecording(audioEnabled: Boolean = false): DashCamResult<ActiveCameraRecording> =
        startRecording(RecordingProfiles.parking(audioEnabled = audioEnabled))

    suspend fun startRecordingWithProfile(profile: RecordingProfile): DashCamResult<ActiveCameraRecording> =
        startRecording(profile)

    suspend fun stopRecording(): DashCamResult<MediaFileEntity> {
        val active = activeRecording
            ?: return DashCamResult.Failure(
                DashCamError.InvalidState(
                    state = "idle",
                    message = "No active recording to stop",
                ),
            )

        return when (val completed = cameraFacade.stopRecording()) {
            is DashCamResult.Failure -> completed
            is DashCamResult.Success -> {
                activeRecording = null
                mediaRepository.registerCompletedVideo(
                    file = completed.value.file,
                    profile = active.profile,
                    createdAt = active.startedAt,
                    durationMs = completed.value.durationMs,
                )
            }
        }
    }

    suspend fun takePhoto(): DashCamResult<MediaFileEntity> {
        val createdAt = clock()
        val mode = activeRecording?.profile?.mode ?: RecordingMode.MANUAL
        val photoFile = directories.photoFile(createdAt)
        if (!photoFile.prepareWritableMediaFile()) {
            return DashCamResult.Failure(DashCamError.StorageUnavailable("Photo path is not writable"))
        }

        return when (val completed = cameraFacade.takePhoto(photoFile, mode)) {
            is DashCamResult.Failure -> completed
            is DashCamResult.Success -> mediaRepository.registerCompletedPhoto(
                file = completed.value.file,
                mode = mode,
                createdAt = completed.value.createdAt,
                resolution = completed.value.resolution,
            )
        }
    }

    private suspend fun startRecording(profile: RecordingProfile): DashCamResult<ActiveCameraRecording> {
        if (activeRecording != null) {
            return DashCamResult.Failure(
                DashCamError.InvalidState(
                    state = "recording",
                    message = "Recording is already active",
                ),
            )
        }

        val createdAt = clock()
        val outputFile = directories.nextVideoFile(profile.mode, createdAt)
        if (!outputFile.prepareWritableMediaFile()) {
            return DashCamResult.Failure(DashCamError.StorageUnavailable("Video path is not writable"))
        }

        return when (val started = cameraFacade.startRecording(outputFile, profile)) {
            is DashCamResult.Failure -> started
            is DashCamResult.Success -> {
                activeRecording = started.value
                started
            }
        }
    }

    private fun File.prepareWritableMediaFile(): Boolean {
        val parent = parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            return false
        }
        return parent.canWrite()
    }
}
