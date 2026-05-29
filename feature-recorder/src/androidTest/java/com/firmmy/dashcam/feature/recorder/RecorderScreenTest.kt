package com.firmmy.dashcam.feature.recorder

import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderScreenTest {
    @Test
    fun fakeRecorderStateSwitchesRecordingModeAndTogglesControls() {
        var state = RecorderUiState(
            mode = RecordingMode.DRIVING,
            recordingStatus = RecordingStatus.IDLE,
        )
        val holder = FakeRecorderStateHolder(
            stateProvider = { state },
            stateUpdater = { state = it },
        )

        holder.toggleRecording()
        assertEquals(RecordingStatus.RECORDING_DRIVING, holder.state.recordingStatus)

        holder.switchMode(RecordingMode.PARKING)
        assertEquals(RecordingMode.PARKING, holder.state.mode)
        assertEquals(RecordingStatus.RECORDING_PARKING, holder.state.recordingStatus)

        holder.toggleAudio()
        assertFalse(holder.state.audioEnabled)

        holder.toggleHotspot()
        assertTrue(holder.state.hotspotEnabled)

        holder.takePhoto()
        assertEquals(1, holder.state.photoCount)

        holder.toggleRecording()
        assertEquals(RecordingStatus.IDLE, holder.state.recordingStatus)
    }

    @Test
    fun mediaBrowserFiltersVideosByDateAndMode() {
        val items = testMediaItems()

        val filtered = filterMediaBrowserItems(
            items = items,
            type = MediaType.VIDEO,
            date = "2026-05-28",
            mode = RecordingMode.DRIVING,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf(1L), filtered.map { it.id })
    }

    @Test
    fun mediaBrowserSeparatesPhotosFromVideos() {
        val filtered = filterMediaBrowserItems(
            items = testMediaItems(),
            type = MediaType.PHOTO,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf(3L), filtered.map { it.id })
    }

    @Test
    fun mediaBrowserSortsNewestFirst() {
        val filtered = filterMediaBrowserItems(
            items = testMediaItems(),
            type = MediaType.VIDEO,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf(2L, 1L), filtered.map { it.id })
    }

    private fun testMediaItems(): List<MediaBrowserItem> =
        listOf(
            mediaItem(
                id = 1L,
                type = MediaType.VIDEO,
                mode = RecordingMode.DRIVING,
                createdAt = 1_779_926_400_000L,
            ),
            mediaItem(
                id = 2L,
                type = MediaType.VIDEO,
                mode = RecordingMode.PARKING,
                createdAt = 1_780_012_800_000L,
            ),
            mediaItem(
                id = 3L,
                type = MediaType.PHOTO,
                mode = RecordingMode.MANUAL,
                createdAt = 1_779_926_400_000L,
            ),
        )

    private fun mediaItem(
        id: Long,
        type: MediaType,
        mode: RecordingMode,
        createdAt: Long,
    ): MediaBrowserItem =
        MediaBrowserItem(
            id = id,
            type = type,
            mode = mode,
            path = "/tmp/media_$id",
            createdAt = createdAt,
            durationMs = if (type == MediaType.VIDEO) 60_000L else null,
            sizeBytes = 1024L,
        )
}
