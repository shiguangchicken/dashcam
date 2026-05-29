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
import java.util.Locale
import kotlinx.coroutines.flow.Flow

data class MediaFilter(
    val type: MediaType? = null,
    val mode: RecordingMode? = null,
    val startCreatedAt: Long? = null,
    val endCreatedAt: Long? = null,
)

data class MediaFileOperationResult(
    val media: MediaFileEntity,
    val bytesFreed: Long = 0L,
    val fileDeleted: Boolean = false,
    val thumbnailDeleted: Boolean = false,
)

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

    fun observeMedia(filter: MediaFilter = MediaFilter()): Flow<List<MediaFileEntity>> =
        mediaRepository.observeFilteredMedia(
            type = filter.type,
            mode = filter.mode,
            startCreatedAt = filter.startCreatedAt,
            endCreatedAt = filter.endCreatedAt,
        )

    suspend fun getMedia(id: Long): MediaFileEntity? =
        mediaRepository.getMediaFile(id)

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

    suspend fun deleteMedia(id: Long): DashCamResult<MediaFileOperationResult> =
        runCatching {
            val media = mediaRepository.getMediaFile(id)
                ?: return DashCamResult.Failure(DashCamError.InvalidState("missing_media_$id", "Media file not found"))
            val file = File(media.path)
            val fileSize = file.length().takeIf { file.exists() } ?: media.sizeBytes
            val fileDeleted = !file.exists() || file.delete()
            val thumbnail = media.thumbnailPath?.let(::File)
            val thumbnailDeleted = thumbnail == null || !thumbnail.exists() || thumbnail.delete()
            val markedDeleted = mediaRepository.markDeleted(id)
            if (!markedDeleted) {
                return DashCamResult.Failure(DashCamError.Unknown("Media delete state update failed"))
            }
            MediaFileOperationResult(
                media = media.copy(deleted = true),
                bytesFreed = if (fileDeleted) fileSize else 0L,
                fileDeleted = fileDeleted,
                thumbnailDeleted = thumbnailDeleted,
            )
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Media delete failed")) },
        )

    suspend fun setMediaLocked(
        id: Long,
        locked: Boolean,
    ): DashCamResult<MediaFileEntity> =
        runCatching {
            val media = mediaRepository.getMediaFile(id)
                ?: return DashCamResult.Failure(DashCamError.InvalidState("missing_media_$id", "Media file not found"))
            if (media.deleted) {
                return DashCamResult.Failure(DashCamError.InvalidState("deleted_media_$id", "Deleted media cannot be locked"))
            }
            val mediaType = MediaType.fromStoredValue(media.type)
            val updatedPath = if (locked && mediaType == MediaType.VIDEO) {
                moveVideoToLockedDirectory(media)
            } else {
                media.path
            }
            val updated = media.copy(path = updatedPath, locked = locked)
            val updatedRows = if (updatedPath != media.path) {
                mediaRepository.updatePathAndLocked(id, updatedPath, locked)
            } else {
                mediaRepository.setLocked(id, locked)
            }
            if (!updatedRows) {
                return DashCamResult.Failure(DashCamError.Unknown("Media lock state update failed"))
            }
            updated
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Media lock failed")) },
        )

    suspend fun deletionCandidates(
        limit: Int = 100,
        allowParking: Boolean = true,
    ): List<MediaFileEntity> =
        mediaRepository.oldestDeletionCandidates(limit = limit, allowParking = allowParking)

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

    private fun moveVideoToLockedDirectory(media: MediaFileEntity): String {
        val source = File(media.path)
        if (!source.exists()) {
            return media.path
        }
        val destination = directories
            .lockedVideoFile(source, media.createdAt)
            .uniqueDestination()
        if (source.absolutePath == destination.absolutePath) {
            return source.absolutePath
        }
        destination.parentFile?.mkdirs()
        val moved = source.renameTo(destination)
        if (!moved) {
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            source.delete()
        }
        return destination.absolutePath
    }

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

    private fun File.uniqueDestination(): File {
        if (!exists()) {
            return this
        }
        val baseName = nameWithoutExtension
        val extension = extension
        val parent = parentFile ?: return this
        var index = 1
        while (true) {
            val candidate = parent.resolve(
                "%s_locked%02d.%s".format(Locale.US, baseName, index, extension),
            )
            if (!candidate.exists()) {
                return candidate
            }
            index++
        }
    }

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
