package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.database.RecordSessionDao
import com.firmmy.dashcam.core.database.RecordSessionEntity
import com.firmmy.dashcam.core.database.RecordSessionRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SegmentRecordingControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val startedAt = Instant.parse("2026-05-28T10:15:30Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun rotatesSegmentAfterConfiguredDurationAndIndexesPreviousFileBeforeRestart() = runBlocking {
        val dao = FakeMediaFileDao()
        val sessionDao = FakeRecordSessionDao()
        val cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt)
        val timer = ControlledSegmentTimer()
        val controller = segmentController(dao, sessionDao, cameraFacade, timer)

        val started = controller.startDrivingRecording(audioEnabled = true, segmentDurationMinutes = 1)

        assertTrue(started is DashCamResult.Success)
        timer.awaitWaitCount(1)
        assertEquals(listOf(60_000L), timer.waits)

        timer.fireNext()
        withTimeout(2_000L) {
            while (dao.inserted.size < 1 || cameraFacade.startedFiles.size < 2) {
                delay(10L)
            }
        }

        assertEquals(1, dao.inserted.size)
        assertEquals(2, cameraFacade.startedFiles.size)
        assertEquals(RecordingMode.DRIVING.storedValue, dao.inserted.first().mode)
    }

    @Test
    fun stopRecordingFinishesRecordSession() = runBlocking {
        val sessionDao = FakeRecordSessionDao()
        val controller = segmentController(
            dao = FakeMediaFileDao(),
            sessionDao = sessionDao,
            cameraFacade = FakeCameraRecordingFacade(startedAt = startedAt),
            timer = ControlledSegmentTimer(),
        )

        val started = controller.startParkingRecording(segmentDurationMinutes = 5)
        val stopped = controller.stopRecording()

        assertTrue(started is DashCamResult.Success)
        assertTrue(stopped is DashCamResult.Success)
        val session = sessionDao.sessions.values.single()
        assertEquals(RecordingMode.PARKING.storedValue, session.mode)
        assertEquals("stopped", session.reason)
        assertTrue(session.endedAt != null)
    }

    @Test
    fun recoveryRegistersUnindexedVideosOnce() = runBlocking {
        val dao = FakeMediaFileDao()
        val directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC"))
        val video = temporaryFolder.root
            .resolve("videos/driving/2026-05-28/20260528_101530_001.mp4")
            .also {
                it.parentFile?.mkdirs()
                it.writeBytes(byteArrayOf(1, 2, 3, 4))
            }
        val repository = DashCamMediaRepository(
            mediaRepository = MediaRepository(dao),
            directories = directories,
            thumbnailGenerator = FakeThumbnailGenerator(),
            zoneId = ZoneId.of("UTC"),
        )

        val first = repository.recoverUnindexedVideos()
        val second = repository.recoverUnindexedVideos()

        assertTrue(first is DashCamResult.Success)
        assertTrue(second is DashCamResult.Success)
        assertEquals(video.absolutePath, dao.inserted.single().path)
        assertEquals(RecordingMode.DRIVING.storedValue, dao.inserted.single().mode)
    }

    private fun segmentController(
        dao: FakeMediaFileDao,
        sessionDao: FakeRecordSessionDao,
        cameraFacade: FakeCameraRecordingFacade,
        timer: SegmentTimer,
    ): SegmentRecordingController {
        val directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC"))
        return SegmentRecordingController(
            recorderManager = CameraRecorderManager(
                directories = directories,
                mediaRepository = DashCamMediaRepository(
                    mediaRepository = MediaRepository(dao),
                    directories = directories,
                    thumbnailGenerator = FakeThumbnailGenerator(),
                    zoneId = ZoneId.of("UTC"),
                ),
                cameraFacade = cameraFacade,
                clock = { startedAt },
            ),
            mediaRepository = DashCamMediaRepository(
                mediaRepository = MediaRepository(dao),
                directories = directories,
                thumbnailGenerator = FakeThumbnailGenerator(),
                zoneId = ZoneId.of("UTC"),
            ),
            recordSessionRepository = RecordSessionRepository(sessionDao),
            scope = scope,
            timer = timer,
            clock = { startedAt },
        )
    }
}

private class ControlledSegmentTimer : SegmentTimer {
    val waits = mutableListOf<Long>()
    private val waiters = mutableListOf<CompletableDeferred<Unit>>()

    override suspend fun waitFor(durationMs: Long) {
        val waiter = CompletableDeferred<Unit>()
        waits += durationMs
        waiters += waiter
        waiter.await()
    }

    suspend fun awaitWaitCount(count: Int) {
        withTimeout(2_000L) {
            while (waiters.size < count) {
                delay(10L)
            }
        }
    }

    fun fireNext() {
        waiters.removeFirst().complete(Unit)
    }
}

private class FakeRecordSessionDao : RecordSessionDao {
    val sessions = mutableMapOf<Long, RecordSessionEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: RecordSessionEntity): Long {
        val id = nextId++
        sessions[id] = entity.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): RecordSessionEntity? = sessions[id]

    override suspend fun finishSession(id: Long, endedAt: Long, reason: String?): Int {
        val entity = sessions[id] ?: return 0
        sessions[id] = entity.copy(endedAt = endedAt, reason = reason)
        return 1
    }
}
