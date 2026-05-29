package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaFileDao
import com.firmmy.dashcam.core.database.MediaFileEntity
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMediaFileDao : MediaFileDao {
    private val media = mutableMapOf<Long, MediaFileEntity>()
    private var nextId = 1L

    val inserted: List<MediaFileEntity>
        get() = media.values.toList()

    override suspend fun insert(entity: MediaFileEntity): Long {
        val id = nextId++
        media[id] = entity.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): MediaFileEntity? = media[id]

    override suspend fun getByPath(path: String): MediaFileEntity? =
        media.values.firstOrNull { it.path == path }

    override fun observeAll(includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { includeDeleted || !it.deleted })

    override fun observeByType(type: String, includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { it.type == type && (includeDeleted || !it.deleted) })

    override fun observeByMode(mode: String, includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { it.mode == mode && (includeDeleted || !it.deleted) })

    override fun observeFiltered(
        type: String?,
        mode: String?,
        startCreatedAt: Long?,
        endCreatedAt: Long?,
        includeDeleted: Boolean,
    ): Flow<List<MediaFileEntity>> =
        flowOf(
            media.values
                .filter { type == null || it.type == type }
                .filter { mode == null || it.mode == mode }
                .filter { startCreatedAt == null || it.createdAt >= startCreatedAt }
                .filter { endCreatedAt == null || it.createdAt < endCreatedAt }
                .filter { includeDeleted || !it.deleted },
        )

    override suspend fun markDeleted(id: Long): Int {
        val entity = media[id] ?: return 0
        media[id] = entity.copy(deleted = true)
        return 1
    }

    override suspend fun setLocked(id: Long, locked: Boolean): Int {
        val entity = media[id] ?: return 0
        media[id] = entity.copy(locked = locked)
        return 1
    }

    override suspend fun updatePathAndLocked(id: Long, path: String, locked: Boolean): Int {
        val entity = media[id] ?: return 0
        media[id] = entity.copy(path = path, locked = locked)
        return 1
    }

    override suspend fun oldestDeletionCandidates(limit: Int, allowParking: Int): List<MediaFileEntity> =
        media.values
            .filter { !it.locked && !it.deleted && (allowParking == 1 || it.mode != RecordingMode.PARKING.storedValue) }
            .sortedBy { it.createdAt }
            .take(limit)
}

class FakeThumbnailGenerator : ThumbnailGenerator {
    var imageThumbnailCount = 0
        private set
    var videoThumbnailCount = 0
        private set

    override suspend fun createImageThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int,
    ): DashCamResult<File> {
        imageThumbnailCount++
        destination.parentFile?.mkdirs()
        destination.writeBytes(byteArrayOf(1, 2, 3))
        return DashCamResult.Success(destination)
    }

    override suspend fun createVideoThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int,
    ): DashCamResult<File> {
        videoThumbnailCount++
        destination.parentFile?.mkdirs()
        destination.writeBytes(byteArrayOf(4, 5, 6))
        return DashCamResult.Success(destination)
    }
}

class FakeCameraRecordingFacade(
    private val startedAt: Instant,
    private val durationMs: Long = 30_000L,
) : CameraRecordingFacade {
    var startedProfile: RecordingProfile? = null
        private set
    var activeFile: File? = null
        private set
    val startedFiles = mutableListOf<File>()
    var photoCount = 0
        private set
    var failStart: DashCamError? = null

    override suspend fun startRecording(
        outputFile: File,
        profile: RecordingProfile,
    ): DashCamResult<ActiveCameraRecording> {
        failStart?.let { return DashCamResult.Failure(it) }
        startedProfile = profile
        activeFile = outputFile
        startedFiles += outputFile
        return DashCamResult.Success(ActiveCameraRecording(outputFile, profile, startedAt))
    }

    override suspend fun stopRecording(): DashCamResult<CompletedCameraRecording> {
        val file = activeFile
            ?: return DashCamResult.Failure(DashCamError.InvalidState("idle", "No fake recording is active"))
        file.writeBytes(byteArrayOf(9, 8, 7, 6))
        activeFile = null
        return DashCamResult.Success(CompletedCameraRecording(file, startedAt, durationMs))
    }

    override suspend fun takePhoto(
        outputFile: File,
        mode: RecordingMode,
    ): DashCamResult<CompletedCameraPhoto> {
        photoCount++
        outputFile.writeBytes(byteArrayOf(7, 8, 9))
        return DashCamResult.Success(
            CompletedCameraPhoto(
                file = outputFile,
                createdAt = startedAt.plusMillis(photoCount.toLong()),
                resolution = DashCamResolution(width = 1920, height = 1080),
            ),
        )
    }
}
