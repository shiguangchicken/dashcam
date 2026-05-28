package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CameraRecorderManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val startedAt = Instant.parse("2026-05-28T10:15:30Z")

    @Test
    fun startStopDrivingRecordingUsesDrivingProfileAndIndexesCompletedVideo() = runBlocking {
        val dao = FakeMediaFileDao()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt)
        val manager = cameraRecorderManager(dao, thumbnailGenerator, cameraFacade)

        val started = manager.startDrivingRecording(audioEnabled = false)

        assertTrue(started is DashCamResult.Success)
        assertEquals(RecordingMode.DRIVING, cameraFacade.startedProfile?.mode)
        assertEquals(false, cameraFacade.startedProfile?.audioEnabled)
        assertEquals(12_000, cameraFacade.startedProfile?.bitrateKbps)
        assertEquals(
            "videos/driving/2026-05-28/20260528_101530_001.mp4",
            cameraFacade.activeFile?.relativeTo(temporaryFolder.root)?.invariantSeparatorsPath,
        )

        val stopped = manager.stopRecording()

        assertTrue(stopped is DashCamResult.Success)
        val entity = (stopped as DashCamResult.Success).value
        assertEquals(MediaType.VIDEO.storedValue, entity.type)
        assertEquals(RecordingMode.DRIVING.storedValue, entity.mode)
        assertEquals(false, entity.hasAudio)
        assertEquals(30_000L, entity.durationMs)
        assertEquals(1, thumbnailGenerator.videoThumbnailCount)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun parkingRecordingUsesLowFpsLowBitrateProfile() = runBlocking {
        val manager = cameraRecorderManager(
            dao = FakeMediaFileDao(),
            thumbnailGenerator = FakeThumbnailGenerator(),
            cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt),
        )

        val started = manager.startParkingRecording()

        assertTrue(started is DashCamResult.Success)
        val active = (started as DashCamResult.Success).value
        assertEquals(RecordingMode.PARKING, active.profile.mode)
        assertEquals(2, active.profile.fps)
        assertEquals(1_000, active.profile.bitrateKbps)
        assertEquals(false, active.profile.audioEnabled)
    }

    @Test
    fun takePhotoIndexesManualOrActiveRecordingMode() = runBlocking {
        val dao = FakeMediaFileDao()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt)
        val manager = cameraRecorderManager(dao, thumbnailGenerator, cameraFacade)

        val manualPhoto = manager.takePhoto()
        manager.startParkingRecording()
        val parkingPhoto = manager.takePhoto()

        assertTrue(manualPhoto is DashCamResult.Success)
        assertTrue(parkingPhoto is DashCamResult.Success)
        assertEquals(RecordingMode.MANUAL.storedValue, (manualPhoto as DashCamResult.Success).value.mode)
        assertEquals(RecordingMode.PARKING.storedValue, (parkingPhoto as DashCamResult.Success).value.mode)
        assertEquals(2, thumbnailGenerator.imageThumbnailCount)
    }

    @Test
    fun refusesSecondRecordingStartUntilCurrentRecordingStops() = runBlocking {
        val manager = cameraRecorderManager(
            dao = FakeMediaFileDao(),
            thumbnailGenerator = FakeThumbnailGenerator(),
            cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt),
        )

        val first = manager.startDrivingRecording()
        val second = manager.startParkingRecording()

        assertTrue(first is DashCamResult.Success)
        assertTrue(second is DashCamResult.Failure)
    }

    private fun cameraRecorderManager(
        dao: FakeMediaFileDao,
        thumbnailGenerator: FakeThumbnailGenerator,
        cameraFacade: FakeCameraRecordingFacade,
    ): CameraRecorderManager {
        val directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC"))
        return CameraRecorderManager(
            directories = directories,
            mediaRepository = DashCamMediaRepository(
                mediaRepository = MediaRepository(dao),
                directories = directories,
                thumbnailGenerator = thumbnailGenerator,
            ),
            cameraFacade = cameraFacade,
            clock = { startedAt },
        )
    }
}
