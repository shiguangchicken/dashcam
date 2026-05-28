package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class DashCamMediaRepository(
    private val mediaRepository: MediaRepository,
    private val directories: DashCamMediaDirectories,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    fun observeVideos(): Flow<List<MediaFileEntity>> =
        mediaRepository.observeMediaByType(MediaType.VIDEO)

    fun observePhotos(): Flow<List<MediaFileEntity>> =
        mediaRepository.observeMediaByType(MediaType.PHOTO)

    suspend fun registerCompletedVideo(
        file: File,
        profile: RecordingProfile,
        createdAt: Instant,
        durationMs: Long,
        locked: Boolean = false,
    ): DashCamResult<MediaFileEntity> {
        val usableFile = file.completedMediaFileOrFailure()
            ?: return DashCamResult.Failure(DashCamError.StorageUnavailable("Video file was not completed"))
        val thumbnailPath = createVideoThumbnail(usableFile, createdAt)
        return insertMedia(
            MediaFileEntity(
                type = MediaType.VIDEO.storedValue,
                mode = profile.mode.storedValue,
                path = usableFile.absolutePath,
                thumbnailPath = thumbnailPath,
                createdAt = createdAt.toEpochMilli(),
                durationMs = durationMs,
                sizeBytes = usableFile.length(),
                width = profile.resolution.width,
                height = profile.resolution.height,
                fps = profile.fps.toDouble(),
                bitrate = profile.bitrateKbps,
                hasAudio = profile.audioEnabled,
                locked = locked,
            ),
        )
    }

    suspend fun registerCompletedPhoto(
        file: File,
        mode: RecordingMode,
        createdAt: Instant,
        resolution: DashCamResolution? = null,
    ): DashCamResult<MediaFileEntity> {
        val usableFile = file.completedMediaFileOrFailure()
            ?: return DashCamResult.Failure(DashCamError.StorageUnavailable("Photo file was not completed"))
        val thumbnailPath = createImageThumbnail(usableFile, createdAt)
        return insertMedia(
            MediaFileEntity(
                type = MediaType.PHOTO.storedValue,
                mode = mode.storedValue,
                path = usableFile.absolutePath,
                thumbnailPath = thumbnailPath,
                createdAt = createdAt.toEpochMilli(),
                sizeBytes = usableFile.length(),
                width = resolution?.width,
                height = resolution?.height,
                hasAudio = false,
            ),
        )
    }

    private suspend fun insertMedia(entity: MediaFileEntity): DashCamResult<MediaFileEntity> =
        runCatching {
            val id = mediaRepository.addMediaFile(entity)
            entity.copy(id = id)
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Media insert failed")) },
        )

    private suspend fun createImageThumbnail(
        source: File,
        createdAt: Instant,
    ): String? {
        val destination = directories.thumbnailFile(MediaType.PHOTO, source, createdAt)
        return when (val result = thumbnailGenerator.createImageThumbnail(source, destination)) {
            is DashCamResult.Success -> result.value.absolutePath
            is DashCamResult.Failure -> null
        }
    }

    private suspend fun createVideoThumbnail(
        source: File,
        createdAt: Instant,
    ): String? {
        val destination = directories.thumbnailFile(MediaType.VIDEO, source, createdAt)
        return when (val result = thumbnailGenerator.createVideoThumbnail(source, destination)) {
            is DashCamResult.Success -> result.value.absolutePath
            is DashCamResult.Failure -> null
        }
    }

    private fun File.completedMediaFileOrFailure(): File? =
        takeIf { it.isFile && it.length() > 0L }
}
