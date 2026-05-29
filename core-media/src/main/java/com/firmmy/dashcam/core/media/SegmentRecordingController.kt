package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.database.RecordSessionRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ActiveSegmentRecording(
    val sessionId: Long,
    val profile: RecordingProfile,
    val segmentDurationMinutes: Int,
    val startedAt: Instant,
)

interface SegmentTimer {
    suspend fun waitFor(durationMs: Long)
}

object CoroutineSegmentTimer : SegmentTimer {
    override suspend fun waitFor(durationMs: Long) {
        delay(durationMs)
    }
}

class SegmentRecordingController(
    private val recorderManager: CameraRecorderManager,
    private val mediaRepository: DashCamMediaRepository,
    private val recordSessionRepository: RecordSessionRepository,
    private val scope: CoroutineScope,
    private val timer: SegmentTimer = CoroutineSegmentTimer,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val mutex = Mutex()
    private var activeSession: MutableSegmentSession? = null

    suspend fun startDrivingRecording(
        audioEnabled: Boolean = true,
        segmentDurationMinutes: Int = 3,
    ): DashCamResult<ActiveSegmentRecording> =
        startRecording(RecordingProfiles.driving(audioEnabled), segmentDurationMinutes)

    suspend fun startParkingRecording(
        audioEnabled: Boolean = false,
        segmentDurationMinutes: Int = 5,
    ): DashCamResult<ActiveSegmentRecording> =
        startRecording(RecordingProfiles.parking(audioEnabled), segmentDurationMinutes)

    suspend fun startRecording(
        profile: RecordingProfile,
        segmentDurationMinutes: Int,
    ): DashCamResult<ActiveSegmentRecording> =
        mutex.withLock {
            if (activeSession != null) {
                return@withLock DashCamResult.Failure(
                    DashCamError.InvalidState("recording", "Segmented recording is already active"),
                )
            }
            if (segmentDurationMinutes !in allowedSegmentDurations) {
                return@withLock DashCamResult.Failure(
                    DashCamError.InvalidSetting("segment_duration_minutes", segmentDurationMinutes.toString()),
                )
            }

            when (val recovered = mediaRepository.recoverUnindexedVideos()) {
                is DashCamResult.Failure -> return@withLock recovered
                is DashCamResult.Success -> Unit
            }

            val startedAt = clock()
            val sessionId = recordSessionRepository.startSession(startedAt.toEpochMilli(), profile.mode)
            when (val started = recorderManager.startRecordingWithProfile(profile)) {
                is DashCamResult.Failure -> {
                    recordSessionRepository.finishSession(
                        id = sessionId,
                        endedAt = clock().toEpochMilli(),
                        reason = "start_failed",
                    )
                    started
                }

                is DashCamResult.Success -> {
                    val session = MutableSegmentSession(
                        sessionId = sessionId,
                        profile = profile,
                        segmentDurationMinutes = segmentDurationMinutes,
                        startedAt = started.value.startedAt,
                    )
                    session.rotationJob = scope.launch { rotateSegments() }
                    activeSession = session
                    DashCamResult.Success(session.toActive())
                }
            }
        }

    suspend fun switchRecording(
        profile: RecordingProfile,
        segmentDurationMinutes: Int,
    ): DashCamResult<ActiveSegmentRecording> {
        if (activeSession != null) {
            when (val stopped = stopRecording(reason = "mode_switch")) {
                is DashCamResult.Failure -> return stopped
                is DashCamResult.Success -> Unit
            }
        }
        return startRecording(profile, segmentDurationMinutes)
    }

    suspend fun stopRecording(reason: String = "stopped"): DashCamResult<Unit> {
        val job = mutex.withLock { activeSession?.rotationJob }
        job?.cancelAndJoin()

        return mutex.withLock {
            val session = activeSession
                ?: return@withLock DashCamResult.Failure(
                    DashCamError.InvalidState("idle", "No segmented recording is active"),
                )

            when (val stopped = recorderManager.stopRecording()) {
                is DashCamResult.Failure -> {
                    recordSessionRepository.finishSession(
                        id = session.sessionId,
                        endedAt = clock().toEpochMilli(),
                        reason = "stop_failed",
                    )
                    activeSession = null
                    stopped
                }

                is DashCamResult.Success -> {
                    recordSessionRepository.finishSession(
                        id = session.sessionId,
                        endedAt = clock().toEpochMilli(),
                        reason = reason,
                    )
                    activeSession = null
                    DashCamResult.Success(Unit)
                }
            }
        }
    }

    suspend fun takePhoto() = recorderManager.takePhoto()

    private suspend fun rotateSegments() {
        while (scope.coroutineContext.isActive) {
            val waitMs = mutex.withLock {
                activeSession?.segmentDurationMinutes?.minutesToMillis() ?: return
            }
            timer.waitFor(waitMs)
            mutex.withLock {
                rotateActiveSegmentLocked()
            }
        }
    }

    private suspend fun rotateActiveSegmentLocked() {
        val session = activeSession ?: return
        when (val stopped = recorderManager.stopRecording()) {
            is DashCamResult.Failure -> {
                recordSessionRepository.finishSession(
                    id = session.sessionId,
                    endedAt = clock().toEpochMilli(),
                    reason = "segment_stop_failed",
                )
                activeSession = null
                return
            }

            is DashCamResult.Success -> Unit
        }

        when (val started = recorderManager.startRecordingWithProfile(session.profile)) {
            is DashCamResult.Failure -> {
                recordSessionRepository.finishSession(
                    id = session.sessionId,
                    endedAt = clock().toEpochMilli(),
                    reason = "segment_start_failed",
                )
                activeSession = null
            }

            is DashCamResult.Success -> {
                activeSession = session.copy(startedAt = started.value.startedAt)
            }
        }
    }

    private fun Int.minutesToMillis(): Long = this * 60_000L

    private data class MutableSegmentSession(
        val sessionId: Long,
        val profile: RecordingProfile,
        val segmentDurationMinutes: Int,
        val startedAt: Instant,
        var rotationJob: Job? = null,
    ) {
        fun toActive(): ActiveSegmentRecording =
            ActiveSegmentRecording(
                sessionId = sessionId,
                profile = profile,
                segmentDurationMinutes = segmentDurationMinutes,
                startedAt = startedAt,
            )
    }

    companion object {
        private val allowedSegmentDurations = setOf(1, 3, 5, 10)
    }
}
