package com.firmmy.dashcam.feature.remote

import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.network.RemoteMediaItem
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteViewerScreenTest {
    @Test
    fun remoteMediaForSelectedTypeReturnsVideosOrPhotos() {
        val video = media(id = 1L, type = MediaType.VIDEO)
        val photo = media(id = 2L, type = MediaType.PHOTO)

        assertEquals(
            listOf(video),
            remoteMediaForSelectedType(
                RemoteViewerUiState(
                    selectedType = MediaType.VIDEO,
                    videos = listOf(video),
                    photos = listOf(photo),
                ),
            ),
        )
        assertEquals(
            listOf(photo),
            remoteMediaForSelectedType(
                RemoteViewerUiState(
                    selectedType = MediaType.PHOTO,
                    videos = listOf(video),
                    photos = listOf(photo),
                ),
            ),
        )
    }

    @Test
    fun remoteDateKeyFormatsCreatedAt() {
        assertEquals(
            "2026-05-29",
            remoteDateKey(
                createdAt = 1_780_012_800_000L,
                zoneId = ZoneId.of("UTC"),
            ),
        )
    }

    private fun media(
        id: Long,
        type: MediaType,
    ): RemoteMediaItem =
        RemoteMediaItem(
            id = id,
            type = type,
            mode = RecordingMode.DRIVING,
            path = "/remote/$id",
            createdAt = 1_780_012_800_000L,
            sizeBytes = 1024L,
        )
}
