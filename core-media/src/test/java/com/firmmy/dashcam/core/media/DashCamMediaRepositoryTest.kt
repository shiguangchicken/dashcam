package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashCamMediaRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val createdAt = Instant.parse("2026-05-28T10:15:30Z")

    @Test
    fun registerCompletedVideoIndexesOnlyExistingCompletedFiles() = runBlocking {
        val dao = FakeMediaFileDao()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val repository = dashCamMediaRepository(dao, thumbnailGenerator)
        val missingFile = temporaryFolder.root.resolve("missing.mp4")

        val missingResult = repository.registerCompletedVideo(
            file = missingFile,
            profile = RecordingProfiles.driving(),
            createdAt = createdAt,
            durationMs = 30_000L,
        )

        assertTrue(missingResult is DashCamResult.Failure)
        assertEquals(0, dao.inserted.size)

        val completedFile = temporaryFolder.root.resolve("video.mp4")
        completedFile.writeBytes(byteArrayOf(1, 2, 3))
        val result = repository.registerCompletedVideo(
            file = completedFile,
            profile = RecordingProfiles.driving(audioEnabled = false),
            createdAt = createdAt,
            durationMs = 30_000L,
        )

        assertTrue(result is DashCamResult.Success)
        val entity = (result as DashCamResult.Success).value
        assertEquals(1L, entity.id)
        assertEquals(MediaType.VIDEO.storedValue, entity.type)
        assertEquals(RecordingMode.DRIVING.storedValue, entity.mode)
        assertEquals(3L, entity.sizeBytes)
        assertEquals(30_000L, entity.durationMs)
        assertEquals(false, entity.hasAudio)
        assertNotNull(entity.thumbnailPath)
        assertEquals(1, thumbnailGenerator.videoThumbnailCount)
    }

    @Test
    fun registerCompletedPhotoStoresThumbnailAndImageMetadata() = runBlocking {
        val dao = FakeMediaFileDao()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val repository = dashCamMediaRepository(dao, thumbnailGenerator)
        val photo = temporaryFolder.root.resolve("photo.jpg")
        photo.writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = repository.registerCompletedPhoto(
            file = photo,
            mode = RecordingMode.MANUAL,
            createdAt = createdAt,
            resolution = DashCamResolution(width = 1280, height = 720),
        )

        assertTrue(result is DashCamResult.Success)
        val entity = (result as DashCamResult.Success).value
        assertEquals(MediaType.PHOTO.storedValue, entity.type)
        assertEquals(RecordingMode.MANUAL.storedValue, entity.mode)
        assertEquals(1280, entity.width)
        assertEquals(720, entity.height)
        assertEquals(false, entity.hasAudio)
        assertNotNull(entity.thumbnailPath)
        assertEquals(1, thumbnailGenerator.imageThumbnailCount)
    }

    @Test
    fun deleteMediaRemovesFilesAndMarksDeleted() = runBlocking {
        val dao = FakeMediaFileDao()
        val repository = dashCamMediaRepository(dao, FakeThumbnailGenerator())
        val video = temporaryFolder.root.resolve("delete.mp4").also { it.writeBytes(ByteArray(12)) }
        val thumbnail = temporaryFolder.root.resolve("thumb.jpg").also { it.writeBytes(ByteArray(3)) }
        val id = MediaRepository(dao).addMediaFile(
            testMedia(path = video.absolutePath, thumbnailPath = thumbnail.absolutePath, sizeBytes = 12L),
        )

        val result = repository.deleteMedia(id)

        assertTrue(result is DashCamResult.Success)
        val value = (result as DashCamResult.Success).value
        assertEquals(12L, value.bytesFreed)
        assertTrue(value.fileDeleted)
        assertTrue(value.thumbnailDeleted)
        assertTrue(MediaRepository(dao).getMediaFile(id)?.deleted == true)
        assertTrue(!video.exists())
        assertTrue(!thumbnail.exists())
    }

    @Test
    fun setMediaLockedMovesVideoToLockedDirectory() = runBlocking {
        val dao = FakeMediaFileDao()
        val repository = dashCamMediaRepository(dao, FakeThumbnailGenerator())
        val video = temporaryFolder.root
            .resolve("videos/driving/2026-05-28/20260528_101530_001.mp4")
            .also {
                it.parentFile?.mkdirs()
                it.writeBytes(ByteArray(8))
            }
        val id = MediaRepository(dao).addMediaFile(testMedia(path = video.absolutePath, createdAt = createdAt.toEpochMilli()))

        val result = repository.setMediaLocked(id, locked = true)

        assertTrue(result is DashCamResult.Success)
        val updated = MediaRepository(dao).getMediaFile(id)
        assertTrue(updated?.locked == true)
        assertTrue(updated?.path?.contains("/videos/locked/2026-05-28/") == true)
        assertTrue(File(updated?.path.orEmpty()).exists())
        assertTrue(!video.exists())
    }

    private fun dashCamMediaRepository(
        dao: FakeMediaFileDao,
        thumbnailGenerator: FakeThumbnailGenerator,
    ): DashCamMediaRepository =
        DashCamMediaRepository(
            mediaRepository = MediaRepository(dao),
            directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC")),
            thumbnailGenerator = thumbnailGenerator,
        )

    private fun testMedia(
        path: String,
        createdAt: Long = 100L,
        thumbnailPath: String? = null,
        sizeBytes: Long = 8L,
        locked: Boolean = false,
    ): MediaFileEntity =
        MediaFileEntity(
            type = MediaType.VIDEO.storedValue,
            mode = RecordingMode.DRIVING.storedValue,
            path = path,
            thumbnailPath = thumbnailPath,
            createdAt = createdAt,
            sizeBytes = sizeBytes,
            hasAudio = true,
            locked = locked,
        )
}
