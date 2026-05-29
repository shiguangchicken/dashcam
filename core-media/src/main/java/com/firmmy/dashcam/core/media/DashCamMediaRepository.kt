package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

class DashCamMediaRepository(
    private val mediaRepository: MediaRepository,
    private val directories: DashCamMediaDirectories,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
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

    suspend fun recoverUnindexedVideos(): DashCamResult<List<MediaFileEntity>> =
        runCatching {
            val paths = directories.ensureBaseDirectories()
            val recovered = mutableListOf<MediaFileEntity>()
            val searchRoots = listOf(
                paths.drivingVideos to RecordingMode.DRIVING,
                paths.parkingVideos to RecordingMode.PARKING,
                paths.lockedVideos to RecordingMode.EVENT,
            )
            searchRoots.forEach { (directory, mode) ->
                directory
                    .walkTopDown()
                    .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) && it.length() > 0L }
                    .forEach { file ->
                        recoverVideoIfNeeded(file, mode)?.let(recovered::add)
                    }
            }
            recovered
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Video recovery failed")) },
        )

    private suspend fun recoverVideoIfNeeded(
        file: File,
        mode: RecordingMode,
    ): MediaFileEntity? {
        mediaRepository.getMediaFileByPath(file.absolutePath)?.let { return null }
        val createdAt = file.createdAtFromName() ?: Instant.ofEpochMilli(file.lastModified())
        val profile = when (mode) {
            RecordingMode.PARKING -> RecordingProfiles.parking(audioEnabled = false)
            RecordingMode.EVENT -> RecordingProfiles.driving(audioEnabled = true).copy(mode = RecordingMode.EVENT)
            else -> RecordingProfiles.driving(audioEnabled = true)
        }
        return when (
            val result = registerCompletedVideo(
                file = file,
                profile = profile,
                createdAt = createdAt,
                durationMs = 0L,
                locked = mode == RecordingMode.EVENT,
            )
        ) {
            is DashCamResult.Success -> result.value
            is DashCamResult.Failure -> null
        }
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

    private fun File.createdAtFromName(): Instant? =
        runCatching {
            LocalDateTime.parse(nameWithoutExtension.take(15), fileTimestampFormatter)
                .atZone(zoneId)
                .toInstant()
        }.getOrNull()

    companion object {
        private val fileTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
