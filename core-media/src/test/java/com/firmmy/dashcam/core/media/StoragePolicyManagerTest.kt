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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoragePolicyManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun storagePolicyDoesNothingWhenStorageIsSufficient() = runBlocking {
        val fixture = fixture(freeBytes = 10_000L)
        fixture.addVideo(name = "old.mp4", createdAt = 100L, size = 100L)

        val result = fixture.manager.enforce(StoragePolicyConfig(maxStorageBytes = 1_000L, minFreeBytes = 1_000L))

        assertTrue(result is DashCamResult.Success)
        assertEquals(0, (result as DashCamResult.Success).value.deletedCount)
        assertEquals(1, fixture.dao.inserted.count { !it.deleted })
    }

    @Test
    fun storagePolicyDeletesOldestUnlockedFilesUntilUnderMaxStorage() = runBlocking {
        val fixture = fixture(freeBytes = 10_000L)
        val first = fixture.addVideo(name = "first.mp4", createdAt = 100L, size = 1_024L)
        val second = fixture.addVideo(name = "second.mp4", createdAt = 200L, size = 1_024L)
        fixture.addVideo(name = "locked.mp4", createdAt = 50L, size = 1_024L, locked = true)

        val result = fixture.manager.enforce(StoragePolicyConfig(maxStorageBytes = 2_500L, minFreeBytes = 0L))

        assertTrue(result is DashCamResult.Success)
        assertEquals(1, (result as DashCamResult.Success).value.deletedCount)
        assertTrue(fixture.repository.getMediaFile(first)?.deleted == true)
        assertFalse(fixture.repository.getMediaFile(second)?.deleted == true)
    }

    @Test
    fun storagePolicyDeletesRepeatedlyUntilFreeSpacePressureExhaustsCandidates() = runBlocking {
        val fixture = fixture(freeBytes = 0L)
        fixture.addVideo(name = "first.mp4", createdAt = 100L, size = 4L)
        fixture.addVideo(name = "second.mp4", createdAt = 200L, size = 4L)

        val result = fixture.manager.enforce(StoragePolicyConfig(maxStorageBytes = 100L, minFreeBytes = 10L))

        assertTrue(result is DashCamResult.Success)
        val value = (result as DashCamResult.Success).value
        assertEquals(2, value.deletedCount)
        assertTrue(value.exhaustedCandidates)
        assertEquals(0, fixture.dao.inserted.count { !it.deleted })
    }

    @Test
    fun storagePolicyCanSkipParkingVideosWhenConfigured() = runBlocking {
        val fixture = fixture(freeBytes = 10_000L)
        val parking = fixture.addVideo(
            name = "parking.mp4",
            createdAt = 100L,
            size = 8L,
            mode = RecordingMode.PARKING,
        )
        val driving = fixture.addVideo(name = "driving.mp4", createdAt = 200L, size = 8L)

        val result = fixture.manager.enforce(
            StoragePolicyConfig(maxStorageBytes = 8L, minFreeBytes = 0L, allowDeleteParkingVideos = false),
        )

        assertTrue(result is DashCamResult.Success)
        assertFalse(fixture.repository.getMediaFile(parking)?.deleted == true)
        assertTrue(fixture.repository.getMediaFile(driving)?.deleted == true)
    }

    private fun fixture(freeBytes: Long): StorageFixture {
        val dao = FakeMediaFileDao()
        val directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC"))
        val mediaRepository = MediaRepository(dao)
        val dashCamMediaRepository = DashCamMediaRepository(
            mediaRepository = mediaRepository,
            directories = directories,
            thumbnailGenerator = FakeThumbnailGenerator(),
            zoneId = ZoneId.of("UTC"),
        )
        return StorageFixture(
            dao = dao,
            repository = mediaRepository,
            directories = directories,
            manager = StoragePolicyManager(
                mediaRepository = dashCamMediaRepository,
                directories = directories,
                freeSpaceProvider = { freeBytes },
                now = { Instant.parse("2026-05-29T00:00:00Z") },
            ),
        )
    }

    private data class StorageFixture(
        val dao: FakeMediaFileDao,
        val repository: MediaRepository,
        val directories: DashCamMediaDirectories,
        val manager: StoragePolicyManager,
    ) {
        suspend fun addVideo(
            name: String,
            createdAt: Long,
            size: Long,
            mode: RecordingMode = RecordingMode.DRIVING,
            locked: Boolean = false,
        ): Long {
            val base = when (mode) {
                RecordingMode.PARKING -> directories.ensureBaseDirectories().parkingVideos
                RecordingMode.EVENT -> directories.ensureBaseDirectories().lockedVideos
                else -> directories.ensureBaseDirectories().drivingVideos
            }
            val file = base.resolve(name).also { it.writeBytes(ByteArray(size.toInt())) }
            return repository.addMediaFile(
                MediaFileEntity(
                    type = MediaType.VIDEO.storedValue,
                    mode = mode.storedValue,
                    path = file.absolutePath,
                    createdAt = createdAt,
                    sizeBytes = size,
                    hasAudio = true,
                    locked = locked,
                ),
            )
        }
    }
}
